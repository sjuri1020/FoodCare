from fastapi import FastAPI, Depends, UploadFile, File, Form, HTTPException, status, Request
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from sqlalchemy.orm import Session
from sqlalchemy import func  # 대소문자 구분 없는 검색을 위해
from pydantic import BaseModel
from typing import List, Optional
from datetime import datetime, timedelta
from database import get_db
from models import Ingredient, Recipe, User, ExpenseCategory, Expense, Allergen, Disease, UserHealthInfo, UserAllergy, UserDisease, VerificationCode, Membership
from uuid import uuid4
import os
from passlib.context import CryptContext
import logging
import random
import string
import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from email.header import Header
from pydantic import BaseModel
import requests
import firebase_admin
from firebase_admin import credentials, messaging
from fastapi import Body
from fastapi import Query

# FastAPI 객체 생성
app = FastAPI()

# CORS 설정
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 이미지 업로드 폴더 경로
UPLOAD_DIR = "uploads"
os.makedirs(UPLOAD_DIR, exist_ok=True)

# 정적 파일 접근 설정
app.mount("/uploads", StaticFiles(directory=UPLOAD_DIR), name="uploads")

# 비밀번호 해싱 도구
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

# 이메일 설정
EMAIL_HOST = "smtp.gmail.com"  # SMTP 서버 주소 (예: Gmail)
EMAIL_PORT = 587  # SMTP 서버 포트 (Gmail은 587 또는 465)
EMAIL_USER = "jj1487659@gmail.com"  # 발신자 이메일 주소
EMAIL_PASSWORD = "qhdc vxvj scvx znjx"  # 앱 비밀번호 (Gmail 경우)
EMAIL_FROM = "FoodCare <jj1487659@gmail.com>"  # 발신자 표시 이름

# 로거 설정
logger = logging.getLogger("uvicorn.error")

# Recipe 조회 응답 모델
class RecipeResponse(BaseModel):
    id: int
    name: str
    summary: Optional[str]
    ingredients: str
    instructions: str
    timetaken: str
    difficultylevel: str
    allergies: Optional[str]
    disease: Optional[str]
    diseasereason: Optional[str]
    category: Optional[str]
    image_url: Optional[str] = None 
    
cred = credentials.Certificate('foodcare-3f13c-10437e6ff817.json')

if not firebase_admin._apps:
    firebase_admin.initialize_app(cred)


def send_fcm_notification(fcm_token, title, body, data=None):
    message = messaging.Message(
        notification=messaging.Notification(
            title=title,
            body=body
        ),
        token=fcm_token,
        data=data or {}  # data 매개변수로 전달
    )
    response = messaging.send(message)
    print(f'FCM 전송 결과: {response}')



# =================== Utility Functions ===================

# 인증 코드 생성 함수
def generate_verification_code(length=6):
    """숫자로만 구성된 n자리 인증 코드 생성"""
    return ''.join(random.choices(string.digits, k=length))

# 이메일 전송 함수
def send_email(to_email, subject, message):
    """이메일 전송 함수"""
    try:
        logger.info(f"이메일 전송 시작: {to_email}")
        logger.info(f"SMTP 설정: {EMAIL_HOST}:{EMAIL_PORT}")
        
        msg = MIMEMultipart()
        msg['From'] = EMAIL_FROM
        msg['To'] = to_email
        msg['Subject'] = Header(subject, 'utf-8')
        
        # HTML 본문 추가
        msg.attach(MIMEText(message, 'html', 'utf-8'))
        
        # SMTP 서버 연결 및 이메일 전송
        with smtplib.SMTP(EMAIL_HOST, EMAIL_PORT) as server:
            logger.info("SMTP 서버 연결 시도")
            server.set_debuglevel(1)  # 디버깅 정보 자세히
            server.starttls()  # TLS 보안 연결
            logger.info(f"로그인 시도: {EMAIL_USER}")
            server.login(EMAIL_USER, EMAIL_PASSWORD)
            logger.info(f"이메일 발송 시도: {to_email}")
            server.send_message(msg)
            logger.info("이메일 전송 성공")
        
        return True
    
    except smtplib.SMTPAuthenticationError as e:
        logger.error(f"SMTP 인증 오류: {str(e)}")
        return False
    except smtplib.SMTPException as e:
        logger.error(f"SMTP 오류: {str(e)}")
        return False
    except Exception as e:
        logger.error(f"이메일 전송 일반 오류: {str(e)}")
        return False

# =================== Verification API Models ===================

# 인증번호 요청 모델
class VerificationRequest(BaseModel):
    name: str
    email: str
    purpose: str = "findId"  # 기본값: 아이디 찾기
    login_id: Optional[str] = None  # 비밀번호 찾기용 추가 필드

# 인증번호 확인 모델
class VerificationConfirm(BaseModel):
    email: str
    code: str
    purpose: str = "findId"  # 기본값: 아이디 찾기

# 응답 모델
class VerificationResponse(BaseModel):
    success: bool
    message: str
    data: Optional[dict] = None

# =================== Verification API Endpoints ===================

# 아이디/비밀번호 찾기 - 인증번호 요청 API
@app.post("/user/verify/request", response_model=VerificationResponse)
def request_verification_code(request: VerificationRequest, db: Session = Depends(get_db)):
    try:
        # 디버깅을 위한 로그 추가
        logger.info(f"인증번호 요청: 이메일={request.email}, 이름={request.name}, 목적={request.purpose}")
        
        # 사용자 찾기 로직 (목적에 따라 다름)
        if request.purpose == "findId":
            # 아이디 찾기 - 이름과 이메일로 사용자 찾기
            email = request.email.lower().strip()
            name = request.name.strip()
            
            # 모든 사용자 로깅 (디버깅용)
            all_users = db.query(User).all()
            logger.info(f"전체 사용자 수: {len(all_users)}")
            for user in all_users:
                logger.info(f"사용자: ID={user.id}, 이메일={user.email}, 이름={user.username}")
            
            # 사용자 검색 - 이름과 이메일 모두 일치해야 함
            user = db.query(User).filter(
                func.lower(User.email) == func.lower(email),
                User.username == name  # 이름 일치 여부 검증 추가
            ).first()
            
            if not user:
                logger.warning(f"사용자를 찾을 수 없음: 이메일={email}, 이름={name}")
                return VerificationResponse(
                    success=False,
                    message="입력하신 이름과 이메일로 가입된 계정을 찾을 수 없습니다."
                )
            
            logger.info(f"사용자 찾음: ID={user.id}, 이메일={user.email}, 이름={user.username}")
            
        elif request.purpose == "resetPassword":
            # 비밀번호 찾기 - 아이디, 이름, 이메일로 사용자 찾기
            if not request.login_id:
                return VerificationResponse(
                    success=False,
                    message="아이디 정보가 필요합니다."
                )
                
            login_id = request.login_id.strip()
            name = request.name.strip()
            email = request.email.lower().strip()
            
            # 사용자 검색 - 아이디, 이름, 이메일 모두 일치해야 함
            user = db.query(User).filter(
                User.login_id == login_id,
                func.lower(User.email) == func.lower(email),
                User.username == name
            ).first()
            
            if not user:
                return VerificationResponse(
                    success=False,
                    message="입력하신 정보와 일치하는 계정을 찾을 수 없습니다."
                )
                
            logger.info(f"비밀번호 찾기 - 사용자 찾음: ID={user.id}, 이메일={user.email}")
        
        else:
            # 지원하지 않는 목적
            return VerificationResponse(
                success=False,
                message="지원하지 않는 요청입니다."
            )
        
        # 이전 미사용 인증코드 삭제
        try:
            db.query(VerificationCode).filter(
                VerificationCode.email == request.email,
                VerificationCode.purpose == request.purpose,
                VerificationCode.is_verified == 0
            ).delete()
            db.commit()
        except Exception as e:
            logger.error(f"이전 인증코드 삭제 오류: {str(e)}")
            db.rollback()
            # 오류가 발생해도 계속 진행
        
        # 새 인증코드 생성
        code = generate_verification_code(6)
        expires_at = datetime.now() + timedelta(minutes=10)  # 10분 후 만료
        
        # 인증코드 저장
        verification = VerificationCode(
            email=request.email,
            verification_code=code,
            purpose=request.purpose,
            expires_at=expires_at,
            user_id=user.id
        )
        
        try:
            db.add(verification)
            db.commit()
        except Exception as e:
            logger.error(f"인증코드 저장 오류: {str(e)}")
            db.rollback()
            return VerificationResponse(
                success=False,
                message="서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
            )
        
        # 이메일 내용 작성
        subject = "FoodCare 인증번호 안내"
        html_content = f"""
        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
            <h2 style="color: #4CAF50;">FoodCare 인증번호 안내</h2>
            <p>안녕하세요, <strong>{request.name}</strong>님!</p>
            <p>요청하신 인증번호는 다음과 같습니다:</p>
            <div style="background-color: #f4f4f4; padding: 15px; font-size: 24px; font-weight: bold; text-align: center; letter-spacing: 5px;">
                {code}
            </div>
            <p>인증번호는 발급 후 10분간 유효합니다.</p>
            <p>감사합니다.<br>FoodCare 팀</p>
        </div>
        """
        
        # 이메일 전송
        email_sent = send_email(request.email, subject, html_content)
        
        if not email_sent:
            # 이메일 전송 실패해도 인증번호는 발급됨 - 로그에만 남기고 성공 응답
            logger.warning(f"이메일 전송 실패: {request.email}")
            return VerificationResponse(
                success=True,
                message="인증번호가 발급되었습니다. 테스트용 인증번호: " + code,
                data={"verification_code": code}  # 개발 환경에서만 사용, 실제 배포 시 제거
            )
        
        return VerificationResponse(
            success=True,
            message="인증번호가 이메일로 전송되었습니다. 인증번호 유효시간은 10분입니다."
        )
    
    except Exception as e:
        logger.exception("인증번호 요청 처리 중 오류 발생")
        # 모든 예외를 잡아서 서버가 크래시되지 않게 함
        return VerificationResponse(
            success=False,
            message="서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
        )

# 아이디/비밀번호 찾기 - 인증번호 확인 API
@app.post("/user/verify/confirm", response_model=VerificationResponse)
def confirm_verification_code(request: VerificationConfirm, db: Session = Depends(get_db)):
    try:
        # 1. 인증번호 유효성 확인
        verification = db.query(VerificationCode).filter(
            VerificationCode.email == request.email,
            VerificationCode.verification_code == request.code,
            VerificationCode.purpose == request.purpose,
            VerificationCode.is_verified == 0,
            VerificationCode.expires_at > datetime.now()
        ).first()

        if not verification:
            return VerificationResponse(
                success=False,
                message="인증번호가 유효하지 않거나 만료되었습니다."
            )

        # 2. 인증번호 사용 처리
        verification.is_verified = 1
        db.commit()

        # 3. 목적에 따라 처리
        if request.purpose == "findId":
            user = db.query(User).filter(User.id == verification.user_id).first()
            if user:
                return VerificationResponse(
                    success=True,
                    message="인증이 완료되었습니다.",
                    data={"login_id": user.login_id}
                )

        elif request.purpose == "resetPassword":
            user = db.query(User).filter(User.id == verification.user_id).first()
            if user:
                # 3-1. 임시 비밀번호 생성
                import random
                import string
                temp_password = ''.join(random.choices(string.ascii_letters + string.digits, k=10))

                # 3-2. 비밀번호 해싱 후 저장
                from passlib.context import CryptContext
                pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")
                user.password = pwd_context.hash(temp_password)
                db.commit()

                # 3-3. 사용자에게 임시 비밀번호 전달
                return VerificationResponse(
                    success=True,
                    message="임시 비밀번호가 발급되었습니다.",
                    data={"password": temp_password}
                )

        return VerificationResponse(
            success=True,
            message="인증이 완료되었습니다."
        )

    except Exception as e:
        logger.exception("인증번호 확인 처리 중 오류 발생")
        return VerificationResponse(
            success=False,
            message="서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
        )

# =================== Ingredient API ===================

# 식자재 등록 (사용자 ID 추가)
@app.post("/ingredients")
def add_ingredient(
    name: str = Form(...),
    location: str = Form(...),
    expiry_date: str = Form(...),
    purchase_date: str = Form(...),
    user_id: int = Form(...),
    image: UploadFile = File(None),
    db: Session = Depends(get_db)
):
    # 사용자 존재 확인
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="사용자를 찾을 수 없습니다.")
    
    image_url = None
    if image:
        filename = f"{uuid4()}.jpg"
        with open(f"{UPLOAD_DIR}/{filename}", "wb") as f:
            f.write(image.file.read())
        image_url = f"/uploads/{filename}"

    new_ingredient = Ingredient(
        name=name,
        location=location,
        expiry_date=datetime.strptime(expiry_date, "%Y-%m-%d").date(),
        purchase_date=datetime.strptime(purchase_date, "%Y-%m-%d").date(),
        image_url=image_url,
        user_id=user_id
    )
    db.add(new_ingredient)
    db.commit()

    return {"message": f"{name} 추가 완료!"}


# 사용자별 식자재 목록 조회
@app.get("/ingredients")
def get_ingredients(user_id: int, db: Session = Depends(get_db)):
    """사용자별 식자재 목록 조회"""
    # 사용자 존재 확인
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="사용자를 찾을 수 없습니다.")
    
    # 해당 사용자의 식자재만 조회
    ingredients = db.query(Ingredient).filter(Ingredient.user_id == user_id).all()
    result = []
    for ing in ingredients:
        result.append({
            "id": ing.id,
            "name": ing.name,
            "location": ing.location,
            "expiry_date": ing.expiry_date.strftime("%Y-%m-%d"),
            "purchase_date": ing.purchase_date.strftime("%Y-%m-%d"),
            "image_url": ing.image_url,
            "user_id": ing.user_id
        })
    return result

# 공유 그룹의 모든 식자재 조회 (구성원 기능)
@app.get("/ingredients/shared/{owner_id}")
def get_shared_ingredients(owner_id: int, db: Session = Depends(get_db)):
    """공유 그룹의 모든 식자재 조회 (구성원 기능)"""
    # 그룹 소유자 확인
    owner = db.query(User).filter(User.id == owner_id).first()
    if not owner:
        raise HTTPException(status_code=404, detail="그룹 소유자를 찾을 수 없습니다.")
    
    # 그룹 내 모든 구성원 ID 조회 (소유자 포함)
    member_ids = [owner_id]  # 소유자 포함
    memberships = db.query(Membership).filter(
        Membership.owner_id == owner_id,
        Membership.status == "accepted"
    ).all()
    
    for membership in memberships:
        member_ids.append(membership.member_id)
    
    # 그룹 내 모든 구성원의 식자재 조회
    ingredients = db.query(Ingredient).filter(Ingredient.user_id.in_(member_ids)).all()
    result = []
    for ing in ingredients:
        # 식자재 소유자 정보 추가
        owner_info = db.query(User).filter(User.id == ing.user_id).first()
        result.append({
            "id": ing.id,
            "name": ing.name,
            "location": ing.location,
            "expiry_date": ing.expiry_date.strftime("%Y-%m-%d"),
            "purchase_date": ing.purchase_date.strftime("%Y-%m-%d"),
            "image_url": ing.image_url,
            "user_id": ing.user_id,
            "userId": ing.user_id,  # Android에서 사용하는 필드명
            "owner_name": owner_info.username if owner_info else "알 수 없음"
        })
    return result

@app.get("/members/my_groups/{user_id}")
def get_my_groups(user_id: int, db: Session = Depends(get_db)):
    """사용자가 속한 모든 그룹 정보 조회 (대표자와 구성원 모두)"""
    result = {
        "as_owner": [],  # 대표자로서 속한 그룹
        "as_member": []  # 구성원으로서 속한 그룹
    }
    
    # 1. 대표자로서 속한 그룹 조회 (본인이 초대한 구성원들)
    owner_memberships = db.query(Membership).filter(
        Membership.owner_id == user_id,
        Membership.status == "accepted"
    ).all()
    
    for membership in owner_memberships:
        member_info = db.query(User).filter(User.id == membership.member_id).first()
        if member_info:
            result["as_owner"].append({
                "group_owner_id": user_id,
                "group_owner_name": "나",
                "member_id": membership.member_id,
                "member_name": member_info.username
            })
    
    # 2. 구성원으로서 속한 그룹 조회 (다른 사람이 나를 초대한 경우)
    member_memberships = db.query(Membership).filter(
        Membership.member_id == user_id,
        Membership.owner_id != user_id,  # 자기 자신이 아닌 경우만
        Membership.status == "accepted"
    ).all()
    
    for membership in member_memberships:
        owner_info = db.query(User).filter(User.id == membership.owner_id).first()
        if owner_info:
            result["as_member"].append({
                "group_owner_id": membership.owner_id,
                "group_owner_name": owner_info.username
            })
    
    return result

# 식자재 삭제 API (소유자 확인 추가)
@app.delete("/ingredients/{ingredient_id}")
def delete_ingredient(ingredient_id: int, user_id: int, db: Session = Depends(get_db)):
    """사용자 본인의 식자재만 삭제 가능"""
    ingredient = db.query(Ingredient).filter(Ingredient.id == ingredient_id).first()
    if not ingredient:
        raise HTTPException(status_code=404, detail="해당 식자재를 찾을 수 없습니다.")
    
    # 소유자 확인 (본인의 식자재만 삭제 가능)
    if ingredient.user_id != user_id:
        raise HTTPException(status_code=403, detail="본인의 식자재만 삭제할 수 있습니다.")

    # 이미지 파일 삭제 처리
    if ingredient.image_url:
        image_path = ingredient.image_url.replace("/uploads/", "uploads/")
        if os.path.exists(image_path):
            os.remove(image_path)

    db.delete(ingredient)
    db.commit()
    return {"message": f"{ingredient.name} 삭제 완료!"}

# =================== Recipe API ===================

@app.get("/recipes", response_model=List[RecipeResponse])
def get_recipes(db: Session = Depends(get_db)):
    recipes = db.query(Recipe).all()
    result = []

    for recipe in recipes:
        result.append({
            "id": recipe.id,
            "name": recipe.name,
            "summary": recipe.summary,
            "ingredients": recipe.ingredients,
            "instructions": recipe.instructions,
            "timetaken": recipe.timetaken,
            "difficultylevel": recipe.difficultylevel,
            "allergies": recipe.allergies,
            "disease": recipe.disease,
            "diseasereason": recipe.diseasereason,
            "category": recipe.category,
            "image_url": recipe.image_url
        })

    return result


@app.get("/recipes/{recipe_id}", response_model=RecipeResponse)
def get_recipe(recipe_id: int, db: Session = Depends(get_db)):
    recipe = db.query(Recipe).filter(Recipe.id == recipe_id).first()
    if recipe is None:
        raise HTTPException(status_code=404, detail="Recipe not found")
    return recipe


# =================== Expense API (가계부 기능) ===================

# 모델 정의
class ExpenseCategoryCreate(BaseModel):
    category_name: str
    category_icon: Optional[str] = None
    user_id: int  # 추가

class ExpenseCategoryResponse(BaseModel):
    category_id: int
    category_name: str
    category_icon: Optional[str] = None
    user_id: int  # 추가
    
class ExpenseCreate(BaseModel):
    category_id: int
    product_name: str
    amount: float
    expense_date: str
    memo: Optional[str] = None
    user_id: int  # 추가

class ExpenseResponse(BaseModel):
    expense_id: int
    category_id: int
    product_name: str
    amount: float
    expense_date: str
    memo: Optional[str] = None
    created_at: str
    category_name: Optional[str] = None
    user_id: int  # 추가

# 카테고리 API 엔드포인트
@app.get("/expense_categories", response_model=List[ExpenseCategoryResponse])
def get_expense_categories(user_id: int, db: Session = Depends(get_db)):
    """사용자별 카테고리 조회 - 기본 카테고리가 없으면 자동 생성"""
    # 사용자 존재 확인
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="사용자를 찾을 수 없습니다.")
    
    # 카테고리 조회
    categories = db.query(ExpenseCategory).filter(ExpenseCategory.user_id == user_id).all()
    
    # 카테고리가 없으면 기본 카테고리 생성
    if not categories:
        try:
            create_default_categories(user_id, db)
            categories = db.query(ExpenseCategory).filter(ExpenseCategory.user_id == user_id).all()
            logger.info(f"사용자 {user_id}의 기본 카테고리 자동 생성 완료")
        except Exception as e:
            logger.error(f"기본 카테고리 자동 생성 실패: {e}")
    
    return categories

@app.post("/expense_categories", response_model=ExpenseCategoryResponse)
def create_expense_category(category: ExpenseCategoryCreate, db: Session = Depends(get_db)):
    """사용자별 카테고리 생성"""
    # 사용자 존재 확인
    user = db.query(User).filter(User.id == category.user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="사용자를 찾을 수 없습니다.")
    
    # 동일 사용자의 동일 카테고리명 중복 체크
    existing = db.query(ExpenseCategory).filter(
        ExpenseCategory.user_id == category.user_id,
        ExpenseCategory.category_name == category.category_name
    ).first()
    if existing:
        raise HTTPException(status_code=400, detail="이미 존재하는 카테고리명입니다.")
    
    new_category = ExpenseCategory(**category.dict())
    db.add(new_category)
    db.commit()
    db.refresh(new_category)
    return new_category

@app.put("/expense_categories/{category_id}", response_model=ExpenseCategoryResponse)
def update_expense_category(category_id: int, category: ExpenseCategoryCreate, db: Session = Depends(get_db)):
    """사용자별 카테고리 수정"""
    db_category = db.query(ExpenseCategory).filter(
        ExpenseCategory.category_id == category_id,
        ExpenseCategory.user_id == category.user_id  # 소유자 확인 추가
    ).first()
    if not db_category:
        raise HTTPException(status_code=404, detail="카테고리를 찾을 수 없습니다.")
    
    # 동일 사용자의 동일 카테고리명 중복 체크 (자기 자신 제외)
    existing = db.query(ExpenseCategory).filter(
        ExpenseCategory.user_id == category.user_id,
        ExpenseCategory.category_name == category.category_name,
        ExpenseCategory.category_id != category_id
    ).first()
    if existing:
        raise HTTPException(status_code=400, detail="이미 존재하는 카테고리명입니다.")
    
    for key, value in category.dict().items():
        setattr(db_category, key, value)
    
    db.commit()
    db.refresh(db_category)
    return db_category

@app.delete("/expense_categories/{category_id}")
def delete_expense_category(category_id: int, user_id: int, db: Session = Depends(get_db)):
    """사용자별 카테고리 삭제"""
    # 해당 카테고리가 사용자 소유인지 확인
    db_category = db.query(ExpenseCategory).filter(
        ExpenseCategory.category_id == category_id,
        ExpenseCategory.user_id == user_id
    ).first()
    if not db_category:
        raise HTTPException(status_code=404, detail="카테고리를 찾을 수 없습니다.")
    
    # 해당 카테고리 지출 내역이 있는지 확인
    expenses = db.query(Expense).filter(Expense.category_id == category_id).first()
    if expenses:
        raise HTTPException(status_code=400, detail="이 카테고리에 속한 지출 내역이 있어 삭제할 수 없습니다.")
    
    db.delete(db_category)
    db.commit()
    return {"message": f"{db_category.category_name} 카테고리가 삭제되었습니다."}

# 지출 내역 API 엔드포인트
@app.get("/expenses", response_model=List[ExpenseResponse])
def get_expenses(user_id: int, db: Session = Depends(get_db)):
    """사용자별 지출 내역 조회"""
    # 사용자 존재 확인
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="사용자를 찾을 수 없습니다.")
    
    # 조인을 통해 카테고리 이름도 같이 가져오기 (사용자별 필터링)
    results = db.query(
        Expense, 
        ExpenseCategory.category_name
    ).join(
        ExpenseCategory, 
        Expense.category_id == ExpenseCategory.category_id
    ).filter(
        Expense.user_id == user_id
    ).all()
    
    response = []
    for expense, category_name in results:
        expense_dict = {
            "expense_id": expense.expense_id,
            "category_id": expense.category_id,
            "product_name": expense.product_name,
            "amount": expense.amount,
            "expense_date": expense.expense_date.strftime("%Y-%m-%d %H:%M"),
            "memo": expense.memo,
            "created_at": expense.created_at.strftime("%Y-%m-%d %H:%M"),
            "category_name": category_name,
            "user_id": expense.user_id
        }
        response.append(expense_dict)
    
    return response

@app.get("/expenses/category/{category_id}", response_model=List[ExpenseResponse])
def get_expenses_by_category(category_id: int, user_id: int, db: Session = Depends(get_db)):
    """사용자별 특정 카테고리의 지출 내역 조회"""
    # 카테고리 소유자 확인
    category = db.query(ExpenseCategory).filter(
        ExpenseCategory.category_id == category_id,
        ExpenseCategory.user_id == user_id
    ).first()
    if not category:
        raise HTTPException(status_code=404, detail="카테고리를 찾을 수 없습니다.")
    
    results = db.query(
        Expense, 
        ExpenseCategory.category_name
    ).join(
        ExpenseCategory, 
        Expense.category_id == ExpenseCategory.category_id
    ).filter(
        Expense.category_id == category_id,
        Expense.user_id == user_id
    ).all()
    
    response = []
    for expense, category_name in results:
        expense_dict = {
            "expense_id": expense.expense_id,
            "category_id": expense.category_id,
            "product_name": expense.product_name,
            "amount": expense.amount,
            "expense_date": expense.expense_date.strftime("%Y-%m-%d %H:%M"),
            "memo": expense.memo,
            "created_at": expense.created_at.strftime("%Y-%m-%d %H:%M"),
            "category_name": category_name,
            "user_id": expense.user_id
        }
        response.append(expense_dict)
    
    return response

@app.post("/expenses", response_model=ExpenseResponse)
def create_expense(expense: ExpenseCreate, db: Session = Depends(get_db)):
    """사용자별 지출 내역 생성 - 디버깅 및 오류 처리 강화"""
    try:
        logger.info(f"지출 저장 요청 시작: {expense.dict()}")
        
        # 1. 사용자 존재 확인
        user = db.query(User).filter(User.id == expense.user_id).first()
        if not user:
            logger.error(f"사용자를 찾을 수 없음: user_id={expense.user_id}")
            raise HTTPException(status_code=404, detail="사용자를 찾을 수 없습니다.")
        
        logger.info(f"사용자 확인됨: {user.username} (ID: {user.id})")
        
        # 2. 카테고리 존재 및 소유자 확인
        category = db.query(ExpenseCategory).filter(
            ExpenseCategory.category_id == expense.category_id,
            ExpenseCategory.user_id == expense.user_id  # 반드시 같은 사용자의 카테고리여야 함
        ).first()
        
        if not category:
            logger.error(f"카테고리를 찾을 수 없음: category_id={expense.category_id}, user_id={expense.user_id}")
            # 사용자의 모든 카테고리 로깅
            user_categories = db.query(ExpenseCategory).filter(ExpenseCategory.user_id == expense.user_id).all()
            logger.info(f"사용자의 카테고리 목록: {[(c.category_id, c.category_name) for c in user_categories]}")
            raise HTTPException(
                status_code=403, 
                detail="존재하지 않거나 접근 권한이 없는 카테고리입니다."
            )
        
        logger.info(f"카테고리 확인됨: {category.category_name} (ID: {category.category_id})")
        
        # 3. 날짜 문자열을 datetime 객체로 변환
        try:
            # 여러 날짜 형식 지원
            date_formats = ["%Y-%m-%d %H:%M", "%Y-%m-%d", "%Y-%m-%d %H:%M:%S"]
            expense_date = None
            
            for fmt in date_formats:
                try:
                    expense_date = datetime.strptime(expense.expense_date, fmt)
                    break
                except ValueError:
                    continue
            
            if expense_date is None:
                raise ValueError(f"지원하지 않는 날짜 형식: {expense.expense_date}")
                
            logger.info(f"날짜 변환 성공: {expense.expense_date} -> {expense_date}")
            
        except Exception as e:
            logger.error(f"날짜 형식 오류: {expense.expense_date}, 오류: {e}")
            raise HTTPException(status_code=400, detail=f"잘못된 날짜 형식입니다: {expense.expense_date}")
        
        # 4. 금액 유효성 검사
        try:
            amount = float(expense.amount)
            if amount <= 0:
                raise ValueError("금액은 0보다 커야 합니다")
            logger.info(f"금액 확인됨: {amount}")
        except Exception as e:
            logger.error(f"금액 형식 오류: {expense.amount}, 오류: {e}")
            raise HTTPException(status_code=400, detail=f"잘못된 금액 형식입니다: {expense.amount}")
        
        # 5. 새 지출 내역 생성
        new_expense = Expense(
            category_id=expense.category_id,
            product_name=expense.product_name.strip(),
            amount=amount,
            expense_date=expense_date,
            memo=expense.memo.strip() if expense.memo else None,
            user_id=expense.user_id,
            created_at=datetime.now()
        )
        
        logger.info(f"지출 객체 생성: {new_expense.__dict__}")
        
        try:
            db.add(new_expense)
            db.flush()  # ID 생성을 위해 flush
            logger.info(f"지출 DB 추가 성공: expense_id={new_expense.expense_id}")
            
            db.commit()
            db.refresh(new_expense)
            logger.info(f"지출 저장 커밋 완료: expense_id={new_expense.expense_id}")
            
        except Exception as e:
            db.rollback()
            logger.error(f"지출 내역 DB 저장 실패: {e}")
            logger.exception("DB 저장 오류 상세:")
            raise HTTPException(status_code=500, detail=f"지출 내역 저장에 실패했습니다: {str(e)}")
        
        # 6. 응답 생성
        response = {
            "expense_id": new_expense.expense_id,
            "category_id": new_expense.category_id,
            "product_name": new_expense.product_name,
            "amount": new_expense.amount,
            "expense_date": new_expense.expense_date.strftime("%Y-%m-%d %H:%M"),
            "memo": new_expense.memo,
            "created_at": new_expense.created_at.strftime("%Y-%m-%d %H:%M"),
            "category_name": category.category_name,
            "user_id": new_expense.user_id
        }
        
        logger.info(f"지출 저장 성공 응답: {response}")
        return response
        
    except HTTPException:
        # HTTPException은 그대로 재발생
        raise
    except Exception as e:
        # 모든 예외를 로깅하고 500 오류 반환
        logger.exception(f"지출 저장 API 전체 오류: {e}")
        raise HTTPException(
            status_code=500, 
            detail=f"서버 내부 오류가 발생했습니다: {str(e)}"
        )


@app.put("/expenses/{expense_id}", response_model=ExpenseResponse)
def update_expense(expense_id: int, expense: ExpenseCreate, db: Session = Depends(get_db)):
    """사용자별 지출 내역 수정"""
    # 지출 내역 및 소유자 확인
    db_expense = db.query(Expense).filter(
        Expense.expense_id == expense_id,
        Expense.user_id == expense.user_id
    ).first()
    if not db_expense:
        raise HTTPException(status_code=404, detail="지출 내역을 찾을 수 없습니다.")
    
    # 카테고리 존재 및 소유자 확인
    category = db.query(ExpenseCategory).filter(
        ExpenseCategory.category_id == expense.category_id,
        ExpenseCategory.user_id == expense.user_id
    ).first()
    if not category:
        raise HTTPException(status_code=404, detail="존재하지 않거나 접근 권한이 없는 카테고리입니다.")
    
    # 날짜 문자열을 datetime 객체로 변환
    expense_date = datetime.strptime(expense.expense_date, "%Y-%m-%d %H:%M")
    
    # 데이터 업데이트
    db_expense.category_id = expense.category_id
    db_expense.product_name = expense.product_name
    db_expense.amount = expense.amount
    db_expense.expense_date = expense_date
    db_expense.memo = expense.memo
    
    db.commit()
    db.refresh(db_expense)
    
    # 응답 생성
    return {
        "expense_id": db_expense.expense_id,
        "category_id": db_expense.category_id,
        "product_name": db_expense.product_name,
        "amount": db_expense.amount,
        "expense_date": db_expense.expense_date.strftime("%Y-%m-%d %H:%M"),
        "memo": db_expense.memo,
        "created_at": db_expense.created_at.strftime("%Y-%m-%d %H:%M"),
        "category_name": category.category_name,
        "user_id": db_expense.user_id
    }

@app.delete("/expenses/{expense_id}")
def delete_expense(expense_id: int, user_id: int, db: Session = Depends(get_db)):
    """사용자별 지출 내역 삭제 - 소유권 검증 강화"""
    # 1. 지출 내역 조회 및 소유자 확인
    db_expense = db.query(Expense).filter(
        Expense.expense_id == expense_id,
        Expense.user_id == user_id  # 반드시 본인의 지출 내역이어야 함
    ).first()
    
    if not db_expense:
        raise HTTPException(
            status_code=404, 
            detail="지출 내역을 찾을 수 없거나 삭제 권한이 없습니다."
        )
    
    # 2. 카테고리 소유권도 다시 한번 확인 (추가 보안)
    category = db.query(ExpenseCategory).filter(
        ExpenseCategory.category_id == db_expense.category_id,
        ExpenseCategory.user_id == user_id
    ).first()
    
    if not category:
        raise HTTPException(
            status_code=403, 
            detail="해당 지출 내역에 대한 접근 권한이 없습니다."
        )
    
    # 3. 삭제 실행
    try:
        db.delete(db_expense)
        db.commit()
        logger.info(f"사용자 {user_id}의 지출 내역 {expense_id} 삭제 완료")
    except Exception as e:
        db.rollback()
        logger.error(f"지출 내역 삭제 실패: {e}")
        raise HTTPException(status_code=500, detail="지출 내역 삭제에 실패했습니다.")
    
    return {"message": "지출 내역이 삭제되었습니다."}

# 월별 지출 내역 요약 API
@app.get("/expenses/summary/monthly")
def get_monthly_expense_summary(
    user_id: int, 
    year: int = None, 
    month: int = None, 
    db: Session = Depends(get_db)
):
    """사용자별 월별 지출 내역 요약 - 디버깅 강화"""
    try:
        logger.info(f"월별 요약 요청: user_id={user_id}, year={year}, month={month}")
        
        # 1. 사용자 존재 확인
        user = db.query(User).filter(User.id == user_id).first()
        if not user:
            logger.error(f"사용자를 찾을 수 없음: user_id={user_id}")
            raise HTTPException(status_code=404, detail="사용자를 찾을 수 없습니다.")
        
        logger.info(f"사용자 확인됨: {user.username}")
        
        # 2. 년도와 월이 제공되지 않은 경우 현재 년도와 월을 사용
        if year is None or month is None:
            current_date = datetime.now()
            year = current_date.year
            month = current_date.month
            logger.info(f"기본 날짜 사용: {year}-{month}")
        
        # 3. 월 시작일과 종료일 계산
        from calendar import monthrange
        start_date = datetime(year, month, 1)
        _, last_day = monthrange(year, month)
        end_date = datetime(year, month, last_day, 23, 59, 59)
        
        logger.info(f"날짜 범위: {start_date} ~ {end_date}")
        
        # 4. 사용자의 카테고리 먼저 확인
        user_categories = db.query(ExpenseCategory).filter(
            ExpenseCategory.user_id == user_id
        ).all()
        
        logger.info(f"사용자 카테고리 수: {len(user_categories)}")
        
        # 카테고리가 없으면 기본 카테고리 생성
        if not user_categories:
            logger.info("기본 카테고리 생성 시작")
            try:
                create_default_categories(user_id, db)
                user_categories = db.query(ExpenseCategory).filter(
                    ExpenseCategory.user_id == user_id
                ).all()
                logger.info(f"기본 카테고리 생성 완료: {len(user_categories)}개")
            except Exception as e:
                logger.error(f"기본 카테고리 생성 실패: {e}")
                # 카테고리 생성 실패해도 계속 진행
        
        # 5. 해당 월의 사용자별 지출 내역 조회
        try:
            expenses_query = db.query(
                Expense,
                ExpenseCategory.category_name
            ).join(
                ExpenseCategory,
                Expense.category_id == ExpenseCategory.category_id
            ).filter(
                Expense.user_id == user_id,
                ExpenseCategory.user_id == user_id,
                Expense.expense_date >= start_date,
                Expense.expense_date <= end_date
            )
            
            expenses = expenses_query.all()
            logger.info(f"조회된 지출 내역: {len(expenses)}개")
            
        except Exception as e:
            logger.error(f"지출 내역 조회 실패: {e}")
            # 조인 실패 시 간단한 조회로 fallback
            expenses = []
        
        # 6. 카테고리별 지출 총액 계산
        category_totals = {}
        total_amount = 0
        
        for expense, category_name in expenses:
            try:
                amount = float(expense.amount)
                total_amount += amount
                
                if category_name in category_totals:
                    category_totals[category_name] += amount
                else:
                    category_totals[category_name] = amount
                    
            except Exception as e:
                logger.error(f"지출 금액 처리 오류: {e}, expense_id={expense.expense_id}")
                continue
        
        logger.info(f"총 지출액: {total_amount}")
        
        # 7. 결과 포맷팅
        categories_summary = []
        
        # 카테고리별 데이터가 없으면 기본 카테고리들을 0원으로 표시
        if not category_totals:
            for category in user_categories:
                categories_summary.append({
                    "category_name": category.category_name,
                    "amount": 0.0,
                    "percentage": 0.0
                })
        else:
            for category_name, amount in category_totals.items():
                percentage = (amount / total_amount) * 100 if total_amount > 0 else 0
                categories_summary.append({
                    "category_name": category_name,
                    "amount": amount,
                    "percentage": round(percentage, 2)
                })
        
        # 금액 기준 내림차순 정렬
        categories_summary.sort(key=lambda x: x["amount"], reverse=True)
        
        result = {
            "year": year,
            "month": month,
            "total_amount": total_amount,
            "categories": categories_summary,
            "user_id": user_id
        }
        
        logger.info(f"응답 데이터: {result}")
        return result
        
    except HTTPException:
        # HTTPException은 그대로 재발생
        raise
    except Exception as e:
        # 모든 예외를 로깅하고 500 오류 반환
        logger.exception(f"월별 요약 API 오류: user_id={user_id}, year={year}, month={month}")
        raise HTTPException(
            status_code=500, 
            detail=f"서버 내부 오류가 발생했습니다: {str(e)}"
        )

# 기본 카테고리 생성 함수 추가
def create_default_categories(user_id: int, db: Session):
    """새 사용자를 위한 기본 카테고리 생성 - 안전성 및 로깅 강화"""
    try:
        logger.info(f"기본 카테고리 생성 시작: user_id={user_id}")
        
        # 사용자 존재 확인
        user = db.query(User).filter(User.id == user_id).first()
        if not user:
            logger.error(f"사용자를 찾을 수 없음: user_id={user_id}")
            raise ValueError(f"사용자 ID {user_id}를 찾을 수 없습니다.")
        
        # 이미 카테고리가 있는지 확인
        existing_categories = db.query(ExpenseCategory).filter(
            ExpenseCategory.user_id == user_id
        ).all()
        
        if existing_categories:
            logger.info(f"사용자 {user_id}는 이미 {len(existing_categories)}개의 카테고리를 가지고 있음")
            return
        
        default_categories = [
            {"name": "외식", "icon": "🍽️"},
            {"name": "배달", "icon": "🛵"},
            {"name": "주류", "icon": "🍺"},
            {"name": "장보기", "icon": "🛒"},
            {"name": "간식", "icon": "🍿"},
            {"name": "기타", "icon": "📦"}
        ]
        
        created_count = 0
        
        for cat_data in default_categories:
            try:
                new_category = ExpenseCategory(
                    category_name=cat_data["name"],
                    category_icon=cat_data["icon"],
                    user_id=user_id
                )
                db.add(new_category)
                created_count += 1
                logger.info(f"카테고리 생성 준비: {cat_data['name']}")
                    
            except Exception as e:
                logger.error(f"개별 카테고리 생성 실패: {cat_data['name']}, 오류: {e}")
                continue
        
        # 커밋 시도
        if created_count > 0:
            try:
                db.commit()
                logger.info(f"기본 카테고리 {created_count}개 생성 완료")
                
                # 생성 확인
                created_categories = db.query(ExpenseCategory).filter(
                    ExpenseCategory.user_id == user_id
                ).all()
                logger.info(f"생성 확인: {[(c.category_id, c.category_name) for c in created_categories]}")
                
            except Exception as e:
                logger.error(f"카테고리 커밋 실패: {e}")
                db.rollback()
                raise
        else:
            logger.warning("생성할 카테고리가 없음")
            
    except Exception as e:
        logger.error(f"기본 카테고리 생성 전체 실패: {e}")
        try:
            db.rollback()
        except:
            pass
        raise
# =================== 공유 모드 가계부 API ===================
# 공유 그룹의 카테고리 조회 (구성원 기능)
@app.get("/expense_categories/shared/{owner_id}", response_model=List[ExpenseCategoryResponse])
def get_shared_expense_categories(owner_id: int, db: Session = Depends(get_db)):
    """공유 그룹의 모든 카테고리 조회 (구성원 기능)"""
    # 그룹 소유자 확인
    owner = db.query(User).filter(User.id == owner_id).first()
    if not owner:
        raise HTTPException(status_code=404, detail="그룹 소유자를 찾을 수 없습니다.")
    
    # 그룹 내 모든 구성원 ID 조회 (소유자 포함)
    member_ids = [owner_id]  # 소유자 포함
    memberships = db.query(Membership).filter(
        Membership.owner_id == owner_id,
        Membership.status == "accepted"
    ).all()
    
    for membership in memberships:
        member_ids.append(membership.member_id)
    
    # 그룹 내 모든 구성원의 카테고리 조회 (주로 소유자의 카테고리가 사용됨)
    categories = db.query(ExpenseCategory).filter(
        ExpenseCategory.user_id.in_(member_ids)
    ).all()
    
    return categories

# 공유 그룹의 지출 내역 조회 (구성원 기능)
@app.get("/expenses/shared/{owner_id}", response_model=List[ExpenseResponse])
def get_shared_expenses(owner_id: int, db: Session = Depends(get_db)):
    """공유 그룹의 모든 지출 내역 조회 (구성원 기능)"""
    # 그룹 소유자 확인
    owner = db.query(User).filter(User.id == owner_id).first()
    if not owner:
        raise HTTPException(status_code=404, detail="그룹 소유자를 찾을 수 없습니다.")
    
    # 그룹 내 모든 구성원 ID 조회 (소유자 포함)
    member_ids = [owner_id]  # 소유자 포함
    memberships = db.query(Membership).filter(
        Membership.owner_id == owner_id,
        Membership.status == "accepted"
    ).all()
    
    for membership in memberships:
        member_ids.append(membership.member_id)
    
    # 그룹 내 모든 구성원의 지출 내역 조회
    results = db.query(
        Expense, 
        ExpenseCategory.category_name,
        User.username
    ).join(
        ExpenseCategory, 
        Expense.category_id == ExpenseCategory.category_id
    ).join(
        User,
        Expense.user_id == User.id
    ).filter(
        Expense.user_id.in_(member_ids)
    ).all()
    
    response = []
    for expense, category_name, username in results:
        expense_dict = {
            "expense_id": expense.expense_id,
            "category_id": expense.category_id,
            "product_name": expense.product_name,
            "amount": expense.amount,
            "expense_date": expense.expense_date.strftime("%Y-%m-%d %H:%M"),
            "memo": expense.memo,
            "created_at": expense.created_at.strftime("%Y-%m-%d %H:%M"),
            "category_name": category_name,
            "user_id": expense.user_id,
            "owner_name": username  # 지출 등록자 이름 추가
        }
        response.append(expense_dict)
    
    return response

# 공유 그룹의 월별 지출 요약 조회
@app.get("/expenses/summary/monthly/shared/{owner_id}")
def get_shared_monthly_expense_summary(
    owner_id: int,
    year: int = None, 
    month: int = None, 
    db: Session = Depends(get_db)
):
    """공유 그룹의 월별 지출 내역 요약"""
    try:
        logger.info(f"공유 그룹 월별 요약 요청: owner_id={owner_id}, year={year}, month={month}")
        
        # 1. 그룹 소유자 확인
        owner = db.query(User).filter(User.id == owner_id).first()
        if not owner:
            logger.error(f"그룹 소유자를 찾을 수 없음: owner_id={owner_id}")
            raise HTTPException(status_code=404, detail="그룹 소유자를 찾을 수 없습니다.")
        
        logger.info(f"그룹 소유자 확인됨: {owner.username}")
        
        # 2. 년도와 월이 제공되지 않은 경우 현재 년도와 월을 사용
        if year is None or month is None:
            current_date = datetime.now()
            year = current_date.year
            month = current_date.month
            logger.info(f"기본 날짜 사용: {year}-{month}")
        
        # 3. 월 시작일과 종료일 계산
        from calendar import monthrange
        start_date = datetime(year, month, 1)
        _, last_day = monthrange(year, month)
        end_date = datetime(year, month, last_day, 23, 59, 59)
        
        logger.info(f"날짜 범위: {start_date} ~ {end_date}")
        
        # 4. 그룹 내 모든 구성원 ID 조회
        member_ids = [owner_id]  # 소유자 포함
        memberships = db.query(Membership).filter(
            Membership.owner_id == owner_id,
            Membership.status == "accepted"
        ).all()
        
        for membership in memberships:
            member_ids.append(membership.member_id)
        
        logger.info(f"그룹 구성원 수: {len(member_ids)}명")
        
        # 5. 그룹 구성원들의 카테고리 확인
        group_categories = db.query(ExpenseCategory).filter(
            ExpenseCategory.user_id.in_(member_ids)
        ).all()
        
        logger.info(f"그룹 카테고리 수: {len(group_categories)}개")
        
        # 6. 해당 월의 그룹 지출 내역 조회
        try:
            expenses_query = db.query(
                Expense,
                ExpenseCategory.category_name
            ).join(
                ExpenseCategory,
                Expense.category_id == ExpenseCategory.category_id
            ).filter(
                Expense.user_id.in_(member_ids),
                ExpenseCategory.user_id.in_(member_ids),
                Expense.expense_date >= start_date,
                Expense.expense_date <= end_date
            )
            
            expenses = expenses_query.all()
            logger.info(f"조회된 공유 그룹 지출 내역: {len(expenses)}개")
            
        except Exception as e:
            logger.error(f"공유 그룹 지출 내역 조회 실패: {e}")
            expenses = []
        
        # 7. 카테고리별 지출 총액 계산
        category_totals = {}
        total_amount = 0
        
        for expense, category_name in expenses:
            try:
                amount = float(expense.amount)
                total_amount += amount
                
                if category_name in category_totals:
                    category_totals[category_name] += amount
                else:
                    category_totals[category_name] = amount
                    
            except Exception as e:
                logger.error(f"지출 금액 처리 오류: {e}, expense_id={expense.expense_id}")
                continue
        
        logger.info(f"공유 그룹 총 지출액: {total_amount}")
        
        # 8. 결과 포맷팅
        categories_summary = []
        
        # 카테고리별 데이터가 없으면 그룹 카테고리들을 0원으로 표시
        if not category_totals:
            for category in group_categories:
                categories_summary.append({
                    "category_name": category.category_name,
                    "amount": 0.0,
                    "percentage": 0.0
                })
        else:
            for category_name, amount in category_totals.items():
                percentage = (amount / total_amount) * 100 if total_amount > 0 else 0
                categories_summary.append({
                    "category_name": category_name,
                    "amount": amount,
                    "percentage": round(percentage, 2)
                })
        
        # 금액 기준 내림차순 정렬
        categories_summary.sort(key=lambda x: x["amount"], reverse=True)
        
        result = {
            "year": year,
            "month": month,
            "total_amount": total_amount,
            "categories": categories_summary,
            "group_owner_id": owner_id,
            "group_owner_name": owner.username,
            "member_count": len(member_ids)
        }
        
        logger.info(f"공유 그룹 응답 데이터: {result}")
        return result
        
    except HTTPException:
        # HTTPException은 그대로 재발생
        raise
    except Exception as e:
        # 모든 예외를 로깅하고 500 오류 반환
        logger.exception(f"공유 그룹 월별 요약 API 오류: owner_id={owner_id}, year={year}, month={month}")
        raise HTTPException(
            status_code=500, 
            detail=f"서버 내부 오류가 발생했습니다: {str(e)}"
        )

# 공유 그룹의 특정 카테고리 지출 내역 조회
@app.get("/expenses/category/shared/{owner_id}/{category_id}", response_model=List[ExpenseResponse])
def get_shared_expenses_by_category(
    owner_id: int, 
    category_id: int, 
    db: Session = Depends(get_db)
):
    """공유 그룹의 특정 카테고리 지출 내역 조회"""
    # 그룹 소유자 확인
    owner = db.query(User).filter(User.id == owner_id).first()
    if not owner:
        raise HTTPException(status_code=404, detail="그룹 소유자를 찾을 수 없습니다.")
    
    # 카테고리 존재 확인
    category = db.query(ExpenseCategory).filter(
        ExpenseCategory.category_id == category_id
    ).first()
    if not category:
        raise HTTPException(status_code=404, detail="카테고리를 찾을 수 없습니다.")
    
    # 그룹 내 모든 구성원 ID 조회
    member_ids = [owner_id]
    memberships = db.query(Membership).filter(
        Membership.owner_id == owner_id,
        Membership.status == "accepted"
    ).all()
    
    for membership in memberships:
        member_ids.append(membership.member_id)
    
    # 해당 카테고리의 그룹 지출 내역 조회
    results = db.query(
        Expense, 
        ExpenseCategory.category_name,
        User.username
    ).join(
        ExpenseCategory, 
        Expense.category_id == ExpenseCategory.category_id
    ).join(
        User,
        Expense.user_id == User.id
    ).filter(
        Expense.category_id == category_id,
        Expense.user_id.in_(member_ids)
    ).all()
    
    response = []
    for expense, category_name, username in results:
        expense_dict = {
            "expense_id": expense.expense_id,
            "category_id": expense.category_id,
            "product_name": expense.product_name,
            "amount": expense.amount,
            "expense_date": expense.expense_date.strftime("%Y-%m-%d %H:%M"),
            "memo": expense.memo,
            "created_at": expense.created_at.strftime("%Y-%m-%d %H:%M"),
            "category_name": category_name,
            "user_id": expense.user_id,
            "owner_name": username
        }
        response.append(expense_dict)
    
    return response

# 공유 그룹 권한 확인 헬퍼 함수
def check_group_member_permission(user_id: int, owner_id: int, db: Session) -> bool:
    """사용자가 해당 그룹의 구성원인지 확인"""
    if user_id == owner_id:
        # 본인이 소유자인 경우
        return True
    
    # 구성원으로 등록되어 있는지 확인
    membership = db.query(Membership).filter(
        Membership.owner_id == owner_id,
        Membership.member_id == user_id,
        Membership.status == "accepted"
    ).first()
    
    return membership is not None

def check_group_owner_permission(user_id: int, owner_id: int, db: Session) -> bool:
    """사용자가 해당 그룹의 소유자인지 확인"""
    return user_id == owner_id

# =================== 유 저 API ===================

class SignupResponse(BaseModel):
    success: bool
    message: str

class UserCreate(BaseModel):
    login_id: str
    username: str
    email: str
    password: str

class UserResponse(BaseModel):
    id: int
    login_id: str
    username: str
    email: str
    profile_image_url: Optional[str] = None


class LoginResponse(BaseModel):
    success: bool
    message: str
    id: int = None
    username: str = ""
    login_id: str = ""
    email: str = ""


# --- 회원가입 ------------------------------------------------------------

@app.post("/user", response_model=SignupResponse, status_code=status.HTTP_200_OK)
def create_user(user: UserCreate, db: Session = Depends(get_db)):
    try:
        # 1) 중복 로그인 ID 검사
        if db.query(User).filter(User.login_id == user.login_id).first():
            return SignupResponse(success=False, message="이미 존재하는 로그인 ID입니다.")
        # 2) 중복 이메일 검사
        if db.query(User).filter(func.lower(User.email) == func.lower(user.email)).first():
            return SignupResponse(success=False, message="이미 존재하는 이메일입니다.")

        # 3) 비밀번호 해시화
        hashed_pw = pwd_context.hash(user.password)
        new_user = User(
            login_id=user.login_id,
            username=user.username,
            email=user.email,
            password=hashed_pw
        )

        # 4) DB에 저장
        db.add(new_user)
        db.commit()
        db.refresh(new_user)
        
        logger.info(f"신규 사용자 생성 완료: ID={new_user.id}, 로그인ID={new_user.login_id}")
        
        # 5) 새 사용자를 위한 기본 카테고리 생성 (트랜잭션 분리)
        try:
            create_default_categories(new_user.id, db)
            logger.info(f"사용자 {new_user.id}의 기본 카테고리 생성 완료")
        except Exception as e:
            logger.error(f"기본 카테고리 생성 실패 (회원가입은 성공): {e}")
            # 카테고리 생성 실패해도 회원가입은 성공으로 처리
        
        return SignupResponse(success=True, message="회원가입 성공!")
        
    except Exception as e:
        logger.exception(f"회원가입 처리 중 오류: {e}")
        db.rollback()
        return SignupResponse(success=False, message=f"회원가입 실패: {str(e)}")
    
# --- 전체 사용자 조회 ----------------------------------------------------

@app.get("/user", response_model=List[UserResponse])
def get_users(db: Session = Depends(get_db)):
    users = db.query(User).all()
    return [
        UserResponse(
            id=user.id,
            username=user.username,
            login_id=user.login_id,
            email=user.email,
            profile_image_url=user.profile_image_url 
        )
        for user in users
    ]

# --- 회원 탈퇴 ----------------------------------------------------------
@app.delete("/user/{login_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_user(login_id: str, db: Session = Depends(get_db)):
    user = db.query(User).filter(User.login_id == login_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    db.delete(user)
    db.commit()
    return

# --- 로그인 --------------------------------------------------------------

class LoginRequest(BaseModel):
    login_id: str
    password: str

@app.post(
    "/login",
    response_model=LoginResponse,
    status_code=status.HTTP_200_OK
)
def login(login_req: LoginRequest, db: Session = Depends(get_db)):
    db_user = db.query(User).filter(User.login_id == login_req.login_id).first()
    if not db_user or not pwd_context.verify(login_req.password, db_user.password):
        return LoginResponse(
            success=False,
            message="로그인 ID 또는 비밀번호가 틀렸습니다.",
            id=None,
            username="",
            login_id="",
            email=""
        )
    return LoginResponse(
        success=True,
        message="로그인 성공!",
        id=db_user.id,
        username=db_user.username,
        login_id=db_user.login_id,
        email=db_user.email
    )




# 비밀번호 변경 모델
class PasswordChangeRequest(BaseModel):
    login_id: str
    current_password: str
    new_password: str

class PasswordChangeResponse(BaseModel):
    success: bool
    message: str

# 비밀번호 변경 API 엔드포인트
@app.post("/user/password/change", response_model=PasswordChangeResponse)
def change_password(request: PasswordChangeRequest, db: Session = Depends(get_db)):
    try:
        # 1. 사용자 찾기
        user = db.query(User).filter(User.login_id == request.login_id).first()
        if not user:
            return PasswordChangeResponse(
                success=False,
                message="사용자를 찾을 수 없습니다."
            )
        
        # 2. 현재 비밀번호 확인
        if not pwd_context.verify(request.current_password, user.password):
            return PasswordChangeResponse(
                success=False,
                message="현재 비밀번호가 일치하지 않습니다."
            )
        
        # 3. 새 비밀번호 유효성 검사
        if len(request.new_password) < 6:
            return PasswordChangeResponse(
                success=False,
                message="새 비밀번호는 6자리 이상이어야 합니다."
            )
        
        # 4. 현재 비밀번호와 새 비밀번호가 같은지 확인
        if pwd_context.verify(request.new_password, user.password):
            return PasswordChangeResponse(
                success=False,
                message="현재 비밀번호와 새 비밀번호가 동일합니다."
            )
        
        # 5. 새 비밀번호 해싱 및 저장
        hashed_new_password = pwd_context.hash(request.new_password)
        user.password = hashed_new_password
        db.commit()
        
        return PasswordChangeResponse(
            success=True,
            message="비밀번호가 성공적으로 변경되었습니다."
        )
    
    except Exception as e:
        logger.exception("비밀번호 변경 처리 중 오류 발생")
        return PasswordChangeResponse(
            success=False,
            message="서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
        )

@app.post("/user/profile_image")
def upload_profile_image(
    login_id: str = Form(...),
    image: UploadFile = File(...),
    db: Session = Depends(get_db)
):
    filename = f"profile_{login_id}_{uuid4()}.jpg"
    with open(f"{UPLOAD_DIR}/{filename}", "wb") as f:
        f.write(image.file.read())
    image_url = f"/uploads/{filename}"

    user = db.query(User).filter(User.login_id == login_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    user.profile_image_url = image_url
    db.commit()
    return {"success": True, "image_url": image_url}


# =================== 개인정보 API ===================

# ====== Pydantic Schema 정의 ======
# ====== 성별 변환 함수 ======
def normalize_gender(gender: str) -> str:
    mapping = {
        "남성": "Male", "여성": "Female", "기타": "Other",
        "Male": "Male", "Female": "Female", "Other": "Other"
    }
    return mapping.get(gender, "Other")  # 혹시라도 잘못 들어오면 "Other"

class AllergenSchema(BaseModel):
    id: int
    code: str
    name: str

class DiseaseSchema(BaseModel):
    id: int
    code: str
    name: str

class HealthInfoCreate(BaseModel):
    user_id: int
    birth_date: str        # "YYYY-MM-DD"
    gender: str
    height_cm: float
    weight_kg: float
    food_preference: str
    allergen_ids: list[int]  = []
    disease_ids:  list[int]  = []

class HealthInfoResponse(BaseModel):
    id: int
    birth_date: str
    gender: str
    height_cm: float
    weight_kg: float
    food_preference: str
    allergens: list[AllergenSchema]
    diseases:  list[DiseaseSchema]
    url: str 

# ====== Allergen API ======
@app.get("/allergens", response_model=list[AllergenSchema])
def list_allergens(db: Session = Depends(get_db)):
    return db.query(Allergen).all()

@app.post("/allergens", response_model=AllergenSchema)
def create_allergen(a: AllergenSchema, db: Session = Depends(get_db)):
    new = Allergen(code=a.code, name=a.name)
    db.add(new)
    db.commit()
    db.refresh(new)
    return new

# ====== Disease API ======
@app.get("/diseases", response_model=list[DiseaseSchema])
def list_diseases(db: Session = Depends(get_db)):
    return db.query(Disease).all()

@app.post("/diseases", response_model=DiseaseSchema)
def create_disease(d: DiseaseSchema, db: Session = Depends(get_db)):
    new = Disease(code=d.code, name=d.name)
    db.add(new)
    db.commit()
    db.refresh(new)
    return new

# ====== Health Info API ======
@app.post("/health_info", response_model=HealthInfoResponse)
def create_health_info(
    payload: HealthInfoCreate,
    request: Request = None,
    db: Session = Depends(get_db)
):
    hi = UserHealthInfo(
        user_id=payload.user_id,
        birth_date = datetime.strptime(payload.birth_date, "%Y-%m-%d").date(),
        gender     = normalize_gender(payload.gender),
        height_cm  = payload.height_cm,
        weight_kg  = payload.weight_kg,
        food_preference = payload.food_preference
    )
    db.add(hi); db.flush()
    for aid in payload.allergen_ids:
        db.add(UserAllergy(user_health_id=hi.id, allergen_id=aid))
    for did in payload.disease_ids:
        db.add(UserDisease(user_health_id=hi.id, disease_id=did))
    db.commit(); db.refresh(hi)
    base = str(request.base_url).rstrip('/')
      # relationships을 강제로 새로 쿼리!
    hi = db.query(UserHealthInfo).get(hi.id)   # <-- 갱신

    allergen_objs = []
    for a in hi.allergies:
        if a and hasattr(a, "id") and a.code and a.name:
            allergen_objs.append(AllergenSchema(id=a.id, code=a.code, name=a.name))
    disease_objs = []
    for d in hi.diseases:
        if d and hasattr(d, "id") and d.code and d.name:
            disease_objs.append(DiseaseSchema(id=d.id, code=d.code, name=d.name))

    return HealthInfoResponse(
        id=hi.id,
        birth_date=hi.birth_date.strftime("%Y-%m-%d"),
        gender=hi.gender,
        height_cm=hi.height_cm,
        weight_kg=hi.weight_kg,
        food_preference=hi.food_preference,
        allergens=allergen_objs,
        diseases=disease_objs,
        url=f"{base}/health_info/{hi.id}"
    )



@app.get("/health_info/{health_info_id}", response_model=HealthInfoResponse)
def get_health_info(health_info_id: int, request: Request, db: Session = Depends(get_db)):
    hi = db.query(UserHealthInfo).get(health_info_id)
    if not hi:
        raise HTTPException(status_code=404, detail="Not found")
    base = str(request.base_url).rstrip('/')
    return HealthInfoResponse(
        id=hi.id,
        birth_date=hi.birth_date.strftime("%Y-%m-%d"),
        gender=hi.gender,
        height_cm=hi.height_cm,
        weight_kg=hi.weight_kg,
        food_preference=hi.food_preference,
        allergens=[AllergenSchema(code=a.code, name=a.name) for a in hi.allergies],
        diseases=[DiseaseSchema(code=d.code, name=d.name) for d in hi.diseases],
        url=f"{base}/health_info/{hi.id}"
    )

# 모든 개인정보 조회 엔드포인트
@app.get("/health_info", response_model=List[HealthInfoResponse])
def list_health_info(
    request: Request,
    user_id: int = Query(..., description="조회할 사용자 ID"),
    db: Session = Depends(get_db)
):
    his = db.query(UserHealthInfo).filter(UserHealthInfo.user_id == user_id).all()
    base = str(request.base_url).rstrip('/')
    return [
        HealthInfoResponse(
            id=hi.id,
            birth_date=hi.birth_date.strftime("%Y-%m-%d"),
            gender=hi.gender,
            height_cm=hi.height_cm,
            weight_kg=hi.weight_kg,
            food_preference=hi.food_preference,
            allergens=[AllergenSchema(id=a.id, code=a.code, name=a.name) for a in hi.allergies],
            diseases=[DiseaseSchema(id=d.id, code=d.code, name=d.name) for d in hi.diseases],
            url=f"{base}/health_info/{hi.id}"
        ) for hi in his
    ]


@app.put("/health_info/{health_info_id}", response_model=HealthInfoResponse)
def update_health_info(
    health_info_id: int,
    payload: HealthInfoCreate,
    request: Request,
    db: Session = Depends(get_db)
):
    try:
        hi = db.query(UserHealthInfo).get(health_info_id)
        if not hi:
            raise HTTPException(status_code=404, detail="Not found")

        hi.birth_date      = datetime.strptime(payload.birth_date, "%Y-%m-%d").date()
        hi.gender          = normalize_gender(payload.gender)   # <-- 한글/영어 모두 지원!
        hi.height_cm       = payload.height_cm
        hi.weight_kg       = payload.weight_kg
        hi.food_preference = payload.food_preference

        db.query(UserAllergy).filter_by(user_health_id=health_info_id).delete()
        db.query(UserDisease).filter_by(user_health_id=health_info_id).delete()
        for aid in payload.allergen_ids:
            db.add(UserAllergy(user_health_id=health_info_id, allergen_id=aid))
        for did in payload.disease_ids:
            db.add(UserDisease(user_health_id=health_info_id, disease_id=did))

        db.commit()
        db.refresh(hi)

        base = str(request.base_url).rstrip('/')
        return HealthInfoResponse(
            id=hi.id,
            birth_date=hi.birth_date.strftime("%Y-%m-%d"),
            gender=hi.gender,
            height_cm=hi.height_cm,
            weight_kg=hi.weight_kg,
            food_preference=hi.food_preference,
            allergens=[AllergenSchema(id=a.id, code=a.code, name=a.name) for a in hi.allergies],
            diseases=[DiseaseSchema(id=d.id, code=d.code, name=d.name) for d in hi.diseases],

            url=f"{base}/health_info/{hi.id}"
        )

    except HTTPException:
        # NotFound 등 의도된 예외는 그대로
        raise
    except Exception as e:
        # 모든 예외를 스택트레이스와 함께 로깅
        logger.exception(f"[PUT /health_info/{health_info_id}] payload={payload!r}")
        # 클라이언트엔 간단히 Internal Server Error만 전달
        raise HTTPException(status_code=500, detail="Internal Server Error")

# =================== Member ===================

class InviteRequest(BaseModel):
    owner_id: int       # 대표(방장) 유저의 id
    member_login_id: str # 초대할 상대의 로그인 아이디

class InviteResponse(BaseModel):
    # 기존
    success: bool = None
    message: str = None
    # 아래 추가!
    id: int = None
    owner_id: int = None
    owner_username: str = None
    member_id: int = None
    member_username: str = None
    status: str = None
    created_at: str = None


@app.post("/members/invite", response_model=InviteResponse)
def invite_member(req: InviteRequest, db: Session = Depends(get_db)):
    # 1. 초대 대상이 존재하는지 확인
    member = db.query(User).filter(User.login_id == req.member_login_id).first()
    if not member:
        return InviteResponse(success=False, message="존재하지 않는 아이디입니다.")

    # 2. 이미 초대한 상태인지 확인
    already = db.query(Membership).filter(
        Membership.owner_id == req.owner_id,
        Membership.member_id == member.id
    ).first()
    if already:
        return InviteResponse(success=False, message="이미 초대한 사용자입니다.")

    # 3. 초대 생성
    try:
        db.add(Membership(owner_id=req.owner_id, member_id=member.id, status="pending"))
        db.commit()
    except Exception as e:
        logger.error(f"Membership 저장 오류: {e}")
        return InviteResponse(success=False, message="DB 저장 오류")

    # === [FCM 발송은 try-except로] ===
    owner = db.query(User).filter(User.id == req.owner_id).first()
    if getattr(member, "fcm_token", None) and owner:
        try:
            send_fcm_notification(
                member.fcm_token,
                title="구성원 초대 알림",
                body=f"{owner.username}님이 당신을 구성원으로 추가하셨습니다",
                data={
                    "type": "invite",
                    "inviter_name": owner.username
                }
            )
        except Exception as e:
            logger.error(f"FCM 전송 오류: {e}")

    logger.info("정상 InviteResponse 반환 직전")
    return InviteResponse(success=True, message="초대 전송 완료! (상대방 수락 필요)")


class MemberResponse(BaseModel):
    id: int
    username: str
    login_id: str
    email: str 
    status: str
    is_owner: bool = False
    profile_image_url: Optional[str] = None 

@app.get("/members/{owner_id}", response_model=list[MemberResponse])
def get_members(owner_id: int, db: Session = Depends(get_db)):
    memberships = db.query(Membership).filter(Membership.owner_id == owner_id).all()
    res = []
    for m in memberships:
        user = db.query(User).filter(User.id == m.member_id).first()
        if not user:
            continue   # 또는 logger로 경고 남김
        res.append(MemberResponse(
            id=user.id,
            username=user.username,
            login_id=user.login_id,
            email=user.email if user.email else "",
            status=m.status,
            is_owner=False,
            profile_image_url=user.profile_image_url
        ))
    # 방장 자기 자신도 리스트에 포함
    owner = db.query(User).filter(User.id == owner_id).first()
    if owner:
        res.insert(0, MemberResponse(
            id=owner.id,
            username=owner.username,
            login_id=owner.login_id,
            email=owner.email if owner.email else "",
            status="accepted",
            is_owner=True,
            profile_image_url=owner.profile_image_url
        ))
    print(res)
    return res


class AcceptInviteRequest(BaseModel):
    member_id: int
    owner_id: int

@app.post("/members/accept", response_model=InviteResponse)
def accept_invite(req: AcceptInviteRequest, db: Session = Depends(get_db)):
    membership = db.query(Membership).filter(
        Membership.owner_id == req.owner_id,
        Membership.member_id == req.member_id,
        Membership.status == "pending"
    ).first()
    if not membership:
        return InviteResponse(success=False, message="초대 내역이 없습니다.")
    membership.status = "accepted"
    db.commit()
    return InviteResponse(success=True, message="수락 완료!")


@app.delete("/members/{owner_id}/{member_id}", response_model=InviteResponse)
def delete_member(owner_id: int, member_id: int, db: Session = Depends(get_db)):
    membership = db.query(Membership).filter(
        Membership.owner_id == owner_id,
        Membership.member_id == member_id
    ).first()
    if not membership:
        return InviteResponse(success=False, message="해당 멤버가 없습니다.")
    db.delete(membership)
    db.commit()
    return InviteResponse(success=True, message="구성원 삭제 완료.")

class MembershipDetailResponse(BaseModel):
    id: int
    owner_id: int
    owner_username: str     
    member_id: int
    member_username: str  
    status: str
    created_at: str

@app.get("/memberships", response_model=List[MembershipDetailResponse])
def list_memberships(db: Session = Depends(get_db)):
    memberships = db.query(Membership).all()
    res = []
    for m in memberships:
        owner = db.query(User).filter(User.id == m.owner_id).first()
        member = db.query(User).filter(User.id == m.member_id).first()
        res.append(MembershipDetailResponse(
            id=m.id,
            owner_id=m.owner_id,
            owner_username=owner.username if owner else "",
            member_id=m.member_id,
            member_username=member.username if member else "",
            status=m.status,
            created_at=m.created_at.strftime("%Y-%m-%d %H:%M:%S")
        ))
    return res


class UpdateFcmTokenRequest(BaseModel):
    login_id: str
    fcm_token: str

@app.post("/user/update_token")
def update_fcm_token(req: UpdateFcmTokenRequest, db: Session = Depends(get_db)):
    user = db.query(User).filter(User.login_id == req.login_id).first()
    if user:
        user.fcm_token = req.fcm_token
        db.commit()
        return {"success": True}
    else:
        raise HTTPException(status_code=404, detail="User not found")

@app.post("/members/accept")
def accept_invite(req: AcceptInviteRequest, db: Session = Depends(get_db)):
    # req: {owner_id, member_id}
    membership = db.query(Membership).filter(
        Membership.owner_id == req.owner_id,
        Membership.member_id == req.member_id,
        Membership.status == "pending"
    ).first()
    if membership:
        membership.status = "accepted"
        db.commit()
        return InviteResponse(success=True, message="초대 수락 완료!")
    else:
        return InviteResponse(success=False, message="초대 정보가 없거나 이미 수락됨")

# FastAPI 예시
@app.get("/members/pending/{member_id}", response_model=List[InviteResponse])
def get_pending_invites(member_id: int, db: Session = Depends(get_db)):
    invites = db.query(Membership).filter(Membership.member_id == member_id, Membership.status == "pending").all()
    return [
        InviteResponse(
            id=invite.id,
            owner_id=invite.owner_id,
            owner_username=invite.owner.username if invite.owner else "",
            member_id=invite.member_id,
            member_username=invite.member.username if invite.member else "",
            status=invite.status,
            created_at=invite.created_at.strftime("%Y-%m-%d %H:%M:%S"),
            success=True,  # 필요시
            message="초대 대기중"  # 필요시
        ) for invite in invites
    ]


class AcceptInviteRequest(BaseModel):
    owner_id: int
    member_id: int

@app.post("/members/accept")
def accept_invite(req: AcceptInviteRequest, db: Session = Depends(get_db)):
    invite = db.query(Membership).filter(
        Membership.owner_id == req.owner_id,
        Membership.member_id == req.member_id,
        Membership.status == "pending"
    ).first()
    if not invite:
        raise HTTPException(status_code=404, detail="초대 없음")
    invite.status = "accepted"
    db.commit()
    return {"success": True, "message": "수락 완료"}

@app.post("/members/create_my_group", response_model=InviteResponse)
def create_my_group(user_id: int = Body(...), db: Session = Depends(get_db)):
    # 이미 본인이 owner/member인 row 있으면 안만듬 (중복 방지)
    exist = db.query(Membership).filter(
        Membership.owner_id == user_id,
        Membership.member_id == user_id
    ).first()
    if exist:
        return InviteResponse(success=True, message="이미 대표 그룹 있음", owner_id=user_id, member_id=user_id, status="accepted")
    # 중복 생성을 방지하며 새 그룹 row 생성
    db.add(Membership(owner_id=user_id, member_id=user_id, status="accepted"))
    db.commit()
    return InviteResponse(success=True, message="나만의 집 생성 완료!", owner_id=user_id, member_id=user_id, status="accepted")

# =================== 레시피 등록===================

# Recipe 생성 요청 모델
class RecipeCreateRequest(BaseModel):
    name: str
    summary: str
    ingredients: str
    instructions: str
    timetaken: str
    difficultylevel: str
    allergies: str
    disease: str
    diseasereason: Optional[str] = None
    category: str

# Recipe 생성 응답 모델
class RecipeCreateResponse(BaseModel):
    success: bool
    message: str
    recipeId: Optional[int] = None

# Recipe 등록 API 엔드포인트
@app.post("/recipes", response_model=RecipeCreateResponse)
def create_recipe(
    name: str = Form(...),
    summary: str = Form(...),
    ingredients: str = Form(...),
    instructions: str = Form(...),
    timetaken: str = Form(...),
    difficultylevel: str = Form(...),
    allergies: str = Form(...),
    disease: str = Form(...),
    diseasereason: str = Form(None),
    category: str = Form(...),
    image: UploadFile = File(None),
    db: Session = Depends(get_db)
):
    image_url = None
    if image:
        filename = f"{uuid4()}.jpg"
        with open(f"{UPLOAD_DIR}/{filename}", "wb") as f:
            f.write(image.file.read())
        image_url = f"/uploads/{filename}"

    new_recipe = Recipe(
        name=name,
        summary=summary,
        ingredients=ingredients,
        instructions=instructions,
        timetaken=timetaken,
        difficultylevel=difficultylevel,
        allergies=allergies,
        disease=disease,
        diseasereason=diseasereason,
        category=category,
        image_url=image_url
    )
    db.add(new_recipe)
    db.commit()
    db.refresh(new_recipe)

    return RecipeCreateResponse(
        success=True,
        message="레시피가 성공적으로 등록되었습니다.",
        recipeId=new_recipe.id
    )

@app.post("/recipes/upload", response_model=RecipeCreateResponse)
def create_recipe_with_image(
    name: str = Form(...),
    summary: str = Form(...),
    ingredients: str = Form(...),
    instructions: str = Form(...),
    timetaken: str = Form(...),
    difficultylevel: str = Form(...),
    allergies: str = Form(...),
    disease: str = Form(...),
    diseasereason: str = Form(None),
    category: str = Form(...),
    image: UploadFile = File(None),
    db: Session = Depends(get_db)
):
    image_url = None
    if image:
        filename = f"{uuid4()}.jpg"
        with open(f"{UPLOAD_DIR}/{filename}", "wb") as f:
            f.write(image.file.read())
        image_url = f"/uploads/{filename}"

    new_recipe = Recipe(
        name=name,
        summary=summary,
        ingredients=ingredients,
        instructions=instructions,
        timetaken=timetaken,
        difficultylevel=difficultylevel,
        allergies=allergies,
        disease=disease,
        diseasereason=diseasereason,
        category=category,
        image_url=image_url
    )
    db.add(new_recipe)
    db.commit()
    db.refresh(new_recipe)
    return RecipeCreateResponse(
        success=True,
        message="레시피가 성공적으로 등록되었습니다.",
        recipeId=new_recipe.id
    )

# =================== 서버 실행 ===================

if __name__ == "__main__":
    import uvicorn
    port = int(os.environ.get("PORT", 8000))
    uvicorn.run("main:app", host="0.0.0.0", port=port)