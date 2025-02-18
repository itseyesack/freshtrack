import base64
import requests
import json
import argparse
import sys

def jpg_to_base64(image_path):
    """
    Converts a JPG image file directly to its Base64 representation.

    Args:
        image_path: The path to the JPG image file.

    Returns:
        The Base64 encoded string, or None if an error occurs.
    """
    try:
        with open(image_path, "rb") as image_file:
            jpg_data = image_file.read()
            encoded_string = base64.b64encode(jpg_data)
            return encoded_string.decode('utf-8')
    except FileNotFoundError:
        print(f"Error: Image file not found at '{image_path}'")
        return None
    except Exception as e:
        print(f"An error occurred: {e}")
        return None


def send_receipt_image(image_path, bearer_token, api_url):
    """
    Sends a receipt image to the specified API endpoint and processes the response.

    Args:
        image_path: Path to the JPEG image file.
        bearer_token:  The Bearer token for authorization.
        api_url: The API endpoint URL.

    Returns:
        The JSON response from the API, or None if an error occurs.
    """
    base64_image = jpg_to_base64(image_path)
    if not base64_image:
        return None

    payload = {
        "stream": False,
        "model": "gpt-4o",
        "messages": [
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": "whats on the receipt? return only a json list of items with their prices and quantities (in the form price = 'total price', unit = 'unit of sale, like ea or /LB', unit_price = 'price per unit'), and if available, the UPC or store number listed, the category if listed, as well as store info. For each item, extrapolate a friendly name based on the abbreviated name on the receipt and context clues. If you cant find a value for a field, use Not Found, but all fields must have a value set! The required properties for an item are: 'name', 'friendly_name', 'quantity', 'price', 'unit_price', 'unit', 'upc'"
                    },
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": f"data:image/jpeg;base64,{base64_image}"
                        }
                    }
                ]
            }
        ],
        "response_format": { 
            "type": "json_schema",
            "json_schema": {
                "name": "receipt_extraction",
                "schema": {
                    "type": "object",
                    "properties": {
                        "receipts": { 
                            "type": "array",
                            "items": {
                                "type": "object",
                                "properties": {
                                    "Store": {"type": "string"},
                                    "Address": {"type": "string"},
                                    "Date": {"type": "string"},
                                    "numItems": {"type": "integer"},
                                    "totalCost": {"type": "number"},
                                    "items": {
                                        "type": "array",
                                        "items": {
                                            "type": "object",
                                            "properties": {
                                                "category": {"type": "string"},
                                                "name": {"type": "string"},
                                                "friendly_name": {"type": "string"},
                                                "quantity": {"type": "integer"},
                                                "price": {"type": "number"},
                                                "unit_price": {"type": "string"},
                                                "unit": {"type": "string"},
                                                "upc": {"type": "string"},
                                            },
                                            "required": ["name", "quantity", "price", "friendly_name", "category"],
                                            "additionalProperties": False
                                        }
                                    }
                                },
                                "required": ["Store", "Address", "Date", "numItems", "totalCost", "items"], #numItems and TotalCost might not always be available.
                                "additionalProperties": False
                            }
                        }
                    },
                    "required": ["receipts"], # Important:  The top-level *must* be "receipts"
                    "additionalProperties": False
                },
                "strict": True
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
        return response.json()
    except requests.exceptions.RequestException as e:
        print(f"Error during API request: {e}")
        print(f"Response status code: {response.status_code if 'response' in locals() else 'N/A'}")
        try:
            print(f"Response content: {response.text if 'response' in locals() else 'N/A'}") #print response text, even if there is an error
        except:
            print("could not decode response text")
        return None
    except Exception as e:
        print(f"response error: {e}")
        return None

