import requests
import json
import argparse

def send_text_prompt(user_input, bearer_token, api_url, static_prompt):
    """
    Sends a text prompt to the specified API endpoint and processes the response.

    Args:
    user_input: The user's input string.
    bearer_token: The Bearer token for authorization.
    api_url: The API endpoint URL.
    static_prompt: The static prompt to prepend.

    Returns:
    The text response from the API, or None if an error occurs.
    """
    full_prompt = f"{static_prompt} {user_input}"

    payload = {
        "stream": False,
        "model": "gemini-2.0-pro-exp-02-05",  # Or gpt-4o
        "messages": [
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": full_prompt
                    }
                ]
            }
        ],
        "response_format": {  # require json schema compliant response
            "type": "json_schema",
            "json_schema": {
                "name": "list_compare",
                "schema": {
                    "type": "object",
                    "properties": {
                        "items_for_removal": {
                            "type": "array",
                            "items": {
                                "type": "string"
                            }
                        },
                        "matched_items": {
                            "type": "array",
                            "items": {
                                "name_on_list": {"type": "string"},
                                "name_on_receipt": {"type": "string"}
                            }
                        }
                    },
                    "required": ["items_for_removal", "items_matched"]
                }
            }
        }
    }

    headers = {
        "Authorization": f"Bearer {bearer_token}",
        "Content-Type": "application/json"
    }

    try:
        response = requests.post(api_url, headers=headers, data=json.dumps(payload))
        response.raise_for_status()  # Raise HTTPError for bad responses (4xx or 5xx)
        json_response = response.json()

        # Extract the content, handling potential variations
        content = json_response.get('choices', [{}])[0].get('message', {}).get('content', None)
        return content

    except requests.exceptions.RequestException as e:
        print(f"Error during API request: {e}")
        print(f"Response status code: {response.status_code if 'response' in locals() else 'N/A'}")
        try:
            print(f"Response content: {response.text if 'response' in locals() else 'N/A'}")
        except:
            print("Could not decode response text")
        return None
    except Exception as e:
        print(f"Response error: {e}")
        return None
