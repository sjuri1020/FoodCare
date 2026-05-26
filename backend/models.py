from sqlalchemy import Column, String, Integer, Date, Text, Float, DateTime, ForeignKey, Enum, Table
from sqlalchemy.types import DECIMAL  # Import DECIMAL from types module
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import relationship
from datetime import datetime
from enum import Enum as PyEnum

Base = declarative_base()

class Ingredient(Base):
    __tablename__ = "ingredients"

    id = Column(Integer, primary_key=True, autoincrement=True)
    name = Column(String(50))
    location = Column(String(50))
    expiry_date = Column(Date)
    purchase_date = Column(Date)
    image_url = Column(String(255))
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    
    # 관계 정의
    user = relationship("User", back_populates="ingredients")

class Recipe(Base):
    __tablename__  = "recipes"
    id = Column(Integer, primary_key=True, autoincrement=True)
    name = Column(String(50))
    summary = Column(String(200))
    ingredients = Column(Text)
    instructions = Column(Text)
    timetaken = Column(String(20))
    difficultylevel = Column(String(20))
    allergies = Column(String(200))
    disease = Column(String(200))
    diseasereason = Column(Text)
    category = Column(String(20))
    image_url = Column(String(255))

class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, autoincrement=True)
    login_id = Column(String(50), unique=True, nullable=False)
    username = Column(String(50), nullable=False)
    email = Column(String(50), unique=True, nullable=False)
    password = Column(String(255), nullable=False)
    profile_image_url = Column(String(255), nullable=True) 
    fcm_token = Column(String(255), nullable=True)
    
    # 관계 정의
    ingredients = relationship("Ingredient", back_populates="user")
    verification_codes = relationship("VerificationCode", back_populates="user")
    expense_categories = relationship("ExpenseCategory", back_populates="user", cascade="all, delete-orphan")
    expenses = relationship("Expense", back_populates="user", cascade="all, delete-orphan")
    health_infos = relationship("UserHealthInfo", back_populates="user")

# =================== 가계부 관련 모델  ===================

class ExpenseCategory(Base):
    __tablename__ = "expense_categories"

    category_id = Column(Integer, primary_key=True, autoincrement=True)
    category_name = Column(String(50), nullable=False)
    category_icon = Column(String(100), nullable=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    
    # 관계 정의 추가
    user = relationship("User", back_populates="expense_categories")
    expenses = relationship("Expense", back_populates="category", cascade="all, delete-orphan")

class Expense(Base):
    __tablename__ = "expenses"

    expense_id = Column(Integer, primary_key=True, autoincrement=True)
    category_id = Column(Integer, ForeignKey("expense_categories.category_id", ondelete="CASCADE"), nullable=False)
    product_name = Column(String(100), nullable=False)
    amount = Column(DECIMAL(10, 2), nullable=False) 
    expense_date = Column(DateTime, nullable=False)
    memo = Column(Text, nullable=True)
    created_at = Column(DateTime, default=datetime.now)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    
    # 관계 정의
    user = relationship("User", back_populates="expenses")
    category = relationship("ExpenseCategory", back_populates="expenses")

class Allergen(Base):
    __tablename__ = "allergen"
    id   = Column(Integer, primary_key=True, autoincrement=True)
    code = Column(String(20), unique=True, nullable=False)
    name = Column(String(50), nullable=False)

class Disease(Base):
    __tablename__ = "disease"
    id   = Column(Integer, primary_key=True, autoincrement=True)
    code = Column(String(20), unique=True, nullable=False)
    name = Column(String(50), nullable=False)

class UserHealthInfo(Base):
    __tablename__ = "user_health_info"
    id              = Column(Integer, primary_key=True, autoincrement=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False) 
    birth_date      = Column(Date)
    gender          = Column(Enum('Male','Female','Other', name="gender_enum"))
    height_cm       = Column(Float)
    weight_kg       = Column(Float)
    food_preference = Column(Enum('한식','일식','중식','아시아요리','양식','디저트', name="food_pref_enum"))

    user = relationship("User", back_populates="health_infos")
    allergies = relationship("Allergen", secondary="user_allergy", back_populates="users")
    diseases  = relationship("Disease",  secondary="user_disease", back_populates="users")
    
class UserAllergy(Base):
    __tablename__ = "user_allergy"
    user_health_id = Column(Integer, ForeignKey("user_health_info.id", ondelete="CASCADE"), primary_key=True)
    allergen_id    = Column(Integer, ForeignKey("allergen.id",        ondelete="RESTRICT"),  primary_key=True)

class UserDisease(Base):
    __tablename__ = "user_disease"
    user_health_id = Column(Integer, ForeignKey("user_health_info.id", ondelete="CASCADE"), primary_key=True)
    disease_id     = Column(Integer, ForeignKey("disease.id",         ondelete="RESTRICT"),  primary_key=True)

class VerificationCode(Base):
    __tablename__ = "verification_codes"

    id = Column(Integer, primary_key=True, autoincrement=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=True)
    email = Column(String(100), nullable=False)
    verification_code = Column(String(10), nullable=False)
    purpose = Column(String(50), nullable=False)
    is_verified = Column(Integer, default=0)
    created_at = Column(DateTime, default=datetime.now)
    expires_at = Column(DateTime, nullable=False)

    user = relationship("User", back_populates="verification_codes")

# 역방향 관계 설정
Allergen.users = relationship("UserHealthInfo", secondary="user_allergy", back_populates="allergies")
Disease.users  = relationship("UserHealthInfo", secondary="user_disease",  back_populates="diseases")

class Membership(Base):
    __tablename__ = "memberships"
    id = Column(Integer, primary_key=True, autoincrement=True)
    owner_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    member_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    status = Column(String(16), nullable=False, default="pending")
    created_at = Column(DateTime, default=datetime.now)
    
    owner = relationship("User", foreign_keys=[owner_id])
    member = relationship("User", foreign_keys=[member_id])