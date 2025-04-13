import requests
import json
import os
from google import genai
from dotenv import load_dotenv

# Load environment variables from a .env file
load_dotenv()

# Get the API key from environment variables
api_key = os.getenv("GEMINI_API_KEY")

# Initialize the client with the API key
client = genai.Client(api_key=api_key)
'''This part can be where when the user check boxes the ingredients
and then presses "generate" it will add all the ingredients they checked
to this list of ingredients. '''
# Define the list of ingredients
ingredients = ["potato", "porkchops", "garlic salt", "onion"]

# Create the contents string using the list of ingredients
prompt = f"""
You are a recipe generator. Based on the following ingredients, and no additional ingredients, generate a recipe in JSON format with these keys: title, items_used, ingredients, instructions, prep_time, cook_time, and servings.
The ingredients are: {', '.join(ingredients)}. Make sure the instructions are detailed and easy to follow.
Please respond only with valid JSON.
"""
# Define the request payload
payload = {
    "model": "gemini-2.0-flash",
    "contents": [prompt]

}

# Make a request to the Gemini API to generate content
response = client.models.generate_content(**payload)

# Parse the JSON response
response_json = response.model_dump_json()

# Print the formatted JSON response
try:
    recipe_json = json.loads(response.text)
    print(json.dumps(recipe_json, indent=2))
except json.JSONDecodeError as e:
    print("Error decoding JSON:", e)
    print("Raw response:", response.text)