from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
import os

# Heroku 환경변수 가져오기
DATABASE_URL = os.environ.get('JAWSDB_URL')

engine = create_engine(DATABASE_URL, pool_recycle=500)

def get_db():
    SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()