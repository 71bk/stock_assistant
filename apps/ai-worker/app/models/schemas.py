"""Pydantic schemas for API request/response models."""

from datetime import date
from decimal import Decimal
from enum import Enum

from pydantic import BaseModel, Field


# ============================================================
# Enums
# ============================================================


class TradeSide(str, Enum):
    """Trade side (買/賣)."""

    BUY = "BUY"
    SELL = "SELL"


class Currency(str, Enum):
    """Supported currencies."""

    TWD = "TWD"
    USD = "USD"


# ============================================================
# OCR Models
# ============================================================


class OcrRequest(BaseModel):
    """OCR request metadata."""

    user_id: str
    filename: str
    content_type: str
    broker: str | None = None


class ParsedTrade(BaseModel):
    """A single parsed trade from OCR."""

    # Required fields
    ticker: str = Field(description="Stock ticker symbol (e.g., '2330', 'AAPL')")
    side: TradeSide = Field(description="Trade side: BUY or SELL")
    quantity: int = Field(description="Number of shares", gt=0)
    price: Decimal = Field(description="Price per share", gt=0)
    trade_date: date = Field(description="Trade date (converted to Western calendar)")

    # Optional fields
    currency: Currency = Field(default=Currency.TWD, description="Transaction currency")
    fee: Decimal | None = Field(default=None, description="Transaction fee")
    tax: Decimal | None = Field(default=None, description="Transaction tax")
    stock_name: str | None = Field(default=None, description="Stock name if available")

    # Parsing metadata
    confidence: float = Field(
        default=1.0,
        ge=0.0,
        le=1.0,
        description="Confidence score for this trade (0.0-1.0)",
    )
    warnings: list[str] = Field(
        default_factory=list,
        description="Any parsing warnings for this trade",
    )
    raw_line: str | None = Field(
        default=None,
        description="Original text line for reference",
    )


class OcrResponse(BaseModel):
    """OCR processing response."""

    raw_text: str = Field(description="Original extracted text")
    trades: list[ParsedTrade] = Field(description="List of parsed trades")
    confidence: float = Field(
        ge=0.0,
        le=1.0,
        description="Overall confidence score (0.0-1.0)",
    )
    warnings: list[str] = Field(
        default_factory=list,
        description="Global parsing warnings",
    )

    # Metadata
    broker_detected: str | None = Field(
        default=None,
        description="Detected broker name",
    )
    original_date_format: str | None = Field(
        default=None,
        description="Detected date format (e.g., 'ROC' for 民國)",
    )
    ocr_method: str | None = Field(
        default=None,
        description="OCR method used: 'tesseract' or 'vision_llm'",
    )


# ============================================================
# RAG Ingestion Models
# ============================================================


class IngestRequest(BaseModel):
    """Document ingestion request."""

    user_id: str
    title: str
    source_type: str = "upload"
    tags: list[str] = Field(default_factory=list)


class IngestResponse(BaseModel):
    """Document ingestion response."""

    document_id: str
    title: str
    chunks_count: int
    status: str = Field(description="Status: pending, processing, completed, failed")
    message: str | None = None


class ChunkInfo(BaseModel):
    """Information about a document chunk."""

    chunk_index: int
    content: str
    start_char: int
    end_char: int
    token_count: int | None = None


# ============================================================
# Common Response Models
# ============================================================


class ErrorResponse(BaseModel):
    """Standard error response."""

    error: str
    detail: str | None = None
    code: str | None = None
