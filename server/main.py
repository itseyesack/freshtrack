from llm_ocr import send_receipt_image
from crop import process_and_save_image
from llm_txt import send_text_prompt

from fastapi import FastAPI, File, UploadFile, Form, HTTPException
from fastapi.responses import JSONResponse
from typing import List
import json
import os
import shutil
import time

app = FastAPI()

bearer_token = os.environ.get("BEARER_TOKEN", "NULLAPIKEY") # Set the BEARER_TOKEN variable in env
api_url = os.environ.get("API_URL", "http://localhost:11434/v1/chat/completions")


@app.post("/process-images/")
async def process_images(files: List[UploadFile] = File(...), reference_list: str = Form(...)):
    """
    Processes uploaded receipt images, extracts purchase data, and compares it
    with a reference shopping list to determine items that can be removed.

    Args:
        files: A list of uploaded image files (receipts).
        reference_list: A comma-separated string representing the reference shopping list.

    Returns:
        A JSON response containing a list of items that can be removed from the
        shopping list. Returns an appropriate error message on failure.
    """
    all_responses = []  # Store combined responses for each file
    temp_dir = "temp_images"
    if not os.path.exists(temp_dir):
        os.makedirs(temp_dir)

    try:
        for file in files:
            try:
                temp_filepath = os.path.join(temp_dir, file.filename)
                with open(temp_filepath, "wb") as buffer:
                    shutil.copyfileobj(file.file, buffer)

                cropped_image_path = process_and_save_image(temp_filepath, temp_dir)
                if not cropped_image_path:
                    raise HTTPException(status_code=500, detail=f"Image cropping failed for {file.filename}")

                ocr_response = send_receipt_image(cropped_image_path, bearer_token, api_url)
                if not ocr_response:
                    raise HTTPException(status_code=500, detail=f"OCR processing failed for {file.filename}")

                try:
                    purchase_data = ocr_response['choices'][0]['message']['content']
                    purchase_data_json = json.loads(purchase_data)
                    purchase_items = []
                    for receipt in purchase_data_json.get('receipts', []):
                        purchase_items.extend(receipt.get('items', []))
                    purchase_data_str = "\n".join([f"{item['name']}[ {item['friendly_name']} ] (Qty: {item['quantity']}, Price: {item['price']}, Category: {item['category']})"
                                                   for item in purchase_items])
                except (KeyError, IndexError, json.JSONDecodeError) as e:
                    print(f"Error extracting purchase  {e}")
                    raise HTTPException(status_code=500,
                                        detail=f"Error extracting purchase data from OCR result for {file.filename}")

                item_list_prompt = f"""
You are a shopping list analyzer. Respond with a JSON list like {{'items_for_removal': list[str]}} of 'items' which may be removed from the list since they have now been purchased based on the following purchase data (extracted from a receipt).
You are also provided with a reference shopping list: {reference_list}
The names of items must be inferred from the purchase data and the reference list. Only return items you are confident are in the purchase data. Also respond with a json list matched_items of each item you think should be removed and the entry on the reciept that you matched to it. Here is the purchase \n{purchase_data_str}
"""
                max_retries = 3
                items_to_remove = {"items": []}  # Initialize with empty list
                for attempt in range(max_retries):
                    llm_response = send_text_prompt("", bearer_token, api_url, item_list_prompt)
                    if not llm_response:
                        if attempt == max_retries - 1:
                            raise HTTPException(status_code=500,
                                                detail=f"LLM processing failed for {file.filename}")
                        else:
                            time.sleep(1)
                            continue

                    try:
                        items_to_remove = json.loads(llm_response)
                        if 'items_for_removal' in items_to_remove:
                            break  # Success, exit retry loop
                        elif attempt == max_retries - 1:
                            print("No 'items_for_removal' key found in LLM response after multiple retries.")
                            break  # keep items_to_remove as empty list.
                        else:
                            print(f"No 'items_for_removal' key (attempt {attempt + 1}), retrying...")
                            time.sleep(1)

                    except (KeyError, json.JSONDecodeError) as e:
                        if attempt == max_retries - 1:
                            print(f"Error parsing LLM response after multiple retries: {e}")
                            raise HTTPException(status_code=500,
                                            detail=f"Error parsing LLM response for {file.filename}.") from e
                        else:
                            print(f"Error parsing LLM response (attempt {attempt + 1}), retrying...")
                            time.sleep(1)
                # Merge the responses, correctly combining with the potentially multiple receipts.
                combined_response = {} # Initialize empty dict
                if 'receipts' in purchase_data_json:
                  combined_response['receipts'] = purchase_data_json['receipts']
                else:
                  combined_response['receipts'] = []

                combined_response['items_for_removal'] = items_to_remove.get('items_for_removal', []) # Get 'items', defaulting to [].
                combined_response['matched_items'] = items_to_remove.get('matched_items', [])

                all_responses.append(combined_response)

            finally:
                if os.path.exists(temp_filepath):
                    os.remove(temp_filepath)
                if 'cropped_image_path' in locals() and os.path.exists(cropped_image_path):
                    os.remove(cropped_image_path)
    
    # If multiple files, still maintain a consistent response structure
        if len(all_responses) == 1:  # If just a single file, return that response directly
            print(all_responses[0])
            return JSONResponse(content=all_responses[0])  # No need for a wrapper
        else: # Multiple files, wrap in a list.
            return JSONResponse(content={"responses": all_responses}) #wrap in 'responses'

    finally:
        if os.path.exists(temp_dir):
            shutil.rmtree(temp_dir)
