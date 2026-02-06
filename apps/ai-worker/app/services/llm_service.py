"""LLM Service - unified interface for multiple LLM providers."""

import base64
import json
from abc import ABC, abstractmethod

import httpx
import structlog

from app.config import LlmProvider, get_settings

logger = structlog.get_logger()


class BaseLlmClient(ABC):
    """Abstract base class for LLM clients."""

    @abstractmethod
    async def chat(
        self,
        messages: list[dict],
        temperature: float = 0.7,
        max_tokens: int = 2048,
        json_mode: bool = False,
    ) -> str:
        """Send a chat completion request."""
        pass

    @abstractmethod
    async def vision(
        self,
        image_base64: str,
        prompt: str,
        media_type: str = "image/jpeg",
    ) -> str:
        """Process an image with vision capabilities."""
        pass


class OpenAIClient(BaseLlmClient):
    """OpenAI API client."""

    def __init__(self) -> None:
        self.settings = get_settings()
        from openai import AsyncOpenAI

        self.client = AsyncOpenAI(
            api_key=self.settings.openai_api_key,
            base_url=self.settings.openai_base_url,
        )

    async def chat(
        self,
        messages: list[dict],
        temperature: float = 0.7,
        max_tokens: int = 2048,
        json_mode: bool = False,
    ) -> str:
        response = await self.client.chat.completions.create(
            model=self.settings.text_model,
            messages=messages,  # type: ignore
            temperature=temperature,
            max_tokens=max_tokens,
            response_format={"type": "json_object"} if json_mode else None,
        )
        return response.choices[0].message.content or ""

    async def vision(
        self,
        image_base64: str,
        prompt: str,
        media_type: str = "image/jpeg",
    ) -> str:
        response = await self.client.chat.completions.create(
            model=self.settings.vision_model,
            messages=[
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": prompt},
                        {
                            "type": "image_url",
                            "image_url": {"url": f"data:{media_type};base64,{image_base64}"},
                        },
                    ],
                }
            ],
            max_tokens=4096,
        )
        return response.choices[0].message.content or ""


class OllamaClient(BaseLlmClient):
    """Ollama local LLM client (FREE)."""

    def __init__(self) -> None:
        self.settings = get_settings()
        self.base_url = self.settings.ollama_base_url

    async def chat(
        self,
        messages: list[dict],
        temperature: float = 0.7,
        max_tokens: int = 2048,
        json_mode: bool = False,
    ) -> str:
        async with httpx.AsyncClient(timeout=120.0) as client:
            response = await client.post(
                f"{self.base_url}/api/chat",
                json={
                    "model": self.settings.text_model,
                    "messages": messages,
                    "stream": False,
                    "options": {
                        "temperature": temperature,
                        "num_predict": max_tokens,
                    },
                    "format": "json" if json_mode else None,
                },
            )
            response.raise_for_status()
            data = response.json()
            return data.get("message", {}).get("content", "")

    async def vision(
        self,
        image_base64: str,
        prompt: str,
        media_type: str = "image/jpeg",
    ) -> str:
        async with httpx.AsyncClient(timeout=300.0) as client:
            response = await client.post(
                f"{self.base_url}/api/chat",
                json={
                    "model": self.settings.vision_model,
                    "messages": [
                        {
                            "role": "user",
                            "content": prompt,
                            "images": [image_base64],  # Ollama uses plain base64
                        }
                    ],
                    "stream": False,
                },
            )
            response.raise_for_status()
            data = response.json()
            return data.get("message", {}).get("content", "")


class GeminiClient(BaseLlmClient):
    """Google Gemini API client (FREE tier available)."""

    def __init__(self) -> None:
        self.settings = get_settings()
        self.api_key = self.settings.gemini_api_key
        self.base_url = "https://generativelanguage.googleapis.com/v1beta"

    async def chat(
        self,
        messages: list[dict],
        temperature: float = 0.7,
        max_tokens: int = 2048,
        json_mode: bool = False,
    ) -> str:
        # Convert OpenAI format to Gemini format
        contents = []
        for msg in messages:
            role = "user" if msg["role"] == "user" else "model"
            if msg["role"] == "system":
                # Gemini handles system prompts differently
                contents.append({"role": "user", "parts": [{"text": msg["content"]}]})
                contents.append({"role": "model", "parts": [{"text": "Understood."}]})
            else:
                contents.append({"role": role, "parts": [{"text": msg["content"]}]})

        async with httpx.AsyncClient(timeout=180.0) as client:  # 增加 timeout 以處理大型文件
            response = await client.post(
                f"{self.base_url}/models/{self.settings.text_model}:generateContent",
                params={"key": self.api_key},
                json={
                    "contents": contents,
                    "generationConfig": {
                        "temperature": temperature,
                        "maxOutputTokens": 8192,  # Increase limit to prevent truncation
                        "responseMimeType": "application/json" if json_mode else "text/plain",
                    },
                },
            )
            if response.status_code != 200:
                logger.error("Gemini API error", status=response.status_code, body=response.text)
                response.raise_for_status()
                
            data = response.json()
            
             # Check for candidates
            if not data.get("candidates"):
                logger.error("Gemini returned no candidates", data=data)
                raise ValueError(f"Gemini returned no candidates. Response: {json.dumps(data, ensure_ascii=False)}")
            
            candidate = data["candidates"][0]
            
            # Check for safety finish reason
            if candidate.get("finishReason") == "SAFETY":
                 raise ValueError(f"Gemini blocked content due to safety filters. Ratings: {json.dumps(candidate.get('safetyRatings'), ensure_ascii=False)}")

            # Check for content and parts
            if not candidate.get("content") or not candidate["content"].get("parts"):
                 raise ValueError(f"Gemini response missing content/parts. Candidate: {json.dumps(candidate, ensure_ascii=False)}")

            return candidate["content"]["parts"][0]["text"]

    async def vision(
        self,
        image_base64: str,
        prompt: str,
        media_type: str = "image/jpeg",
    ) -> str:
        url = f"{self.base_url}/models/{self.settings.vision_model}:generateContent"
        logger.info("Gemini vision request", url=url)
        
        async with httpx.AsyncClient(timeout=60.0) as client:
            response = await client.post(
                url,
                params={"key": self.api_key},
                json={
                    "contents": [
                        {
                            "parts": [
                                {"text": prompt},
                                {
                                    "inline_data": {
                                        "mime_type": media_type,
                                        "data": image_base64,
                                    }
                                },
                            ]
                        }
                    ],
                    "generationConfig": {
                        "maxOutputTokens": 8192,
                    },
                },
            )
            if response.status_code != 200:
                logger.error("Gemini API error", status=response.status_code, body=response.text)
                response.raise_for_status()
                
            data = response.json()
            
            # Check for candidates
            if not data.get("candidates"):
                logger.error("Gemini returned no candidates", data=data)
                raise ValueError(f"Gemini returned no candidates. Response: {json.dumps(data, ensure_ascii=False)}")
            
            candidate = data["candidates"][0]
            
            # Check for safety finish reason
            if candidate.get("finishReason") == "SAFETY":
                 raise ValueError(f"Gemini blocked content due to safety filters. Ratings: {json.dumps(candidate.get('safetyRatings'), ensure_ascii=False)}")

            # Check for content and parts
            if not candidate.get("content") or not candidate["content"].get("parts"):
                 raise ValueError(f"Gemini response missing content/parts. Candidate: {json.dumps(candidate, ensure_ascii=False)}")

            return candidate["content"]["parts"][0]["text"]


def get_llm_client(provider: LlmProvider | None = None) -> BaseLlmClient:
    """Get the appropriate LLM client based on settings."""
    settings = get_settings()
    resolved = provider or settings.llm_provider

    if resolved == LlmProvider.OPENAI:
        logger.info("Using OpenAI provider")
        return OpenAIClient()
    elif resolved == LlmProvider.OLLAMA:
        logger.info("Using Ollama provider (FREE & LOCAL)")
        return OllamaClient()
    else:  # Gemini
        logger.info("Using Google Gemini provider")
        return GeminiClient()


class LlmService:
    """Unified LLM service that works with any provider."""

    def __init__(self, provider: LlmProvider | None = None) -> None:
        self.settings = get_settings()
        self.provider = provider or self.settings.llm_provider
        self.client = get_llm_client(self.provider)

    async def chat(
        self,
        messages: list[dict],
        temperature: float = 0.7,
        max_tokens: int = 2048,
        json_mode: bool = False,
    ) -> str:
        """Send a chat completion request."""
        logger.debug("LLM chat request", provider=self.provider.value)
        return await self.client.chat(messages, temperature, max_tokens, json_mode)

    async def vision(
        self,
        image_base64: str,
        prompt: str,
        media_type: str = "image/jpeg",
    ) -> str:
        """Process an image with vision capabilities."""
        try:
            logger.debug(
                "LLM vision request",
                provider=self.provider.value,
                model=self.settings.vision_model,
            )
            return await self.client.vision(image_base64, prompt, media_type)
        except Exception as e:
            logger.error(
                "LLM vision request failed",
                provider=self.settings.llm_provider.value,
                model=self.settings.vision_model,
                error=str(e),
            )
            raise e

    async def generate_summary(self, text: str, max_length: int = 200) -> str:
        """Generate a summary of the given text."""
        messages = [
            {"role": "system", "content": f"請用繁體中文總結以下內容，最多 {max_length} 字。"},
            {"role": "user", "content": text},
        ]
        return await self.chat(messages, temperature=0.3)
