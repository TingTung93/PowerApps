import os
from openai import OpenAI

# --- Client Configuration ---
# The client is configured with the custom base URL of the STARK API
# and your API key.
client = OpenAI(
    base_url="https://api.genai.mil/v1",
    api_key = os.getenv("GENAI_API_KEY")
)

# --- Make the API Call ---
try:
    print("Sending request with the OpenAI library...")
    chat_completion = client.chat.completions.create(
        messages=[
            {
                "role": "user",
                "content": "Say hello",
            }
        ],
        model="gemini-2.5-flash", # Use one of the models available to you
    )

    # --- Print the Result ---
    print("\nCompletion received:")
    print(chat_completion.choices[0].message.content)

except Exception as e:
    print(f"An error occurred: {e}")

