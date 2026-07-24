import os
import json
import requests

# 1. Read the API key from the environment variables
api_key = os.getenv("GENAI_API_KEY")
model_to_test = "gemini-2.5-flash"

print("--- GenAI API Connection Test ---")

if not api_key:
    print("\nERROR: GENAI_API_KEY environment variable not found.")
    print("Please ensure you have set it globally and restarted your terminal.")
else:
    print(f"✅ GENAI_API_KEY found. Testing connection...")

    # 2. Define the endpoint and headers
    url = "https://api.genai.mil/v1/chat/completions"
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json"
    }

    # 3. Define the request payload
    data = {
        "model": model_to_test,
        "messages": [
            {"role": "user", "content": "This is a test. Say 'hello'."}
        ],
        "max_tokens": 10
    }

    try:
        # 4. Make the POST request
        response = requests.post(url, headers=headers, data=json.dumps(data))

        # 5. Print the results
        print(f"\nRequest sent to: {url}")
        print(f"Status Code: {response.status_code}")
        
        print("\n--- Response Body ---")
        # Try to print formatted JSON, fall back to raw text if it fails
        try:
            print(json.dumps(response.json(), indent=2))
        except json.JSONDecodeError:
            print(response.text)
        print("---------------------")

        if response.status_code == 200:
            print("\n✅ SUCCESS: The API key and connection are working correctly.")
            print("The issue is likely with how Opencode is loading its configuration file.")
        else:
            print(f"\n❌ FAILURE: The API returned a {response.status_code} error.")
            print("This suggests the problem is with the API key itself or network access, not Opencode.")


    except requests.exceptions.RequestException as e:
        print(f"\n❌ NETWORK ERROR: Failed to connect to the server.")
        print(f"Error details: {e}")
        print("This could be a firewall, proxy, or DNS issue.")

print("\n--- Test Complete ---")

