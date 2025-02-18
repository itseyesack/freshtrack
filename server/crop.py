import cv2
import numpy as np
from collections import Counter
import uuid
import os
from typing import Optional

def add_black_frame(image: np.ndarray, frame_width: int = 120) -> np.ndarray:
    """Adds a black frame of specified width around an image."""
    height, width = image.shape[:2]
    num_channels = 1 if len(image.shape) == 2 else image.shape[2]  # Handle grayscale and color

    # Create a new, larger image filled with black
    new_height = height + 2 * frame_width
    new_width = width + 2 * frame_width
    if num_channels == 1:
        framed_image = np.zeros((new_height, new_width), dtype=image.dtype)
    else:
        framed_image = np.zeros((new_height, new_width, num_channels), dtype=image.dtype)

    # Copy the original image into the center of the new image
    framed_image[frame_width:frame_width + height, frame_width:frame_width + width] = image
    return framed_image

def is_grayscale(color: tuple[int, int, int], threshold: int = 20, min_val: int = 100) -> bool:
    """Checks if an RGB color is a shade of gray."""
    r, g, b = color

    # Calculate the maximum difference between components
    max_diff = max(abs(r - g), abs(r - b), abs(g - b))

    # Check if the maximum difference is within the threshold and at least two components meet min_val
    return max_diff <= threshold and sum(1 for val in (r, g, b) if val >= min_val) >= 2

def create_tiled_border(image: np.ndarray, tile_size: int = 128, border_width: int = 128) -> np.ndarray:
    """Creates a tiled border using the most common non-grayscale color."""
    height, width = image.shape[:2]
    num_channels = 1 if len(image.shape) == 2 else image.shape[2]
    #print(f"Image dimensions: {height}x{width}, Channels: {num_channels}") # Debug

    # 1. Sample squares around the border
    #print("1. Sampling squares around the border...") # Debug
    samples = []
    for x in range(0, width - tile_size + 1, tile_size):
        samples.append(image[0:tile_size, x:x + tile_size])
        samples.append(image[height - tile_size:height, x:x + tile_size])
    for y in range(tile_size, height - tile_size + 1, tile_size):
        samples.append(image[y:y + tile_size, 0:tile_size])
        samples.append(image[y:y + tile_size, width - tile_size:width])
    #print(f" Sampled {len(samples)} tiles.") # Debug

    # 2. Calculate average colors and find the most common non-grayscale
    #print("2. Finding most common non-grayscale color...") # Debug
    average_colors = [tuple(np.mean(sample, axis=(0, 1)).astype(int)) for sample in samples]
    color_counts = Counter(average_colors)

    tile = None
    for avg_color, count in color_counts.most_common():
        #print(f" Checking color: {avg_color}, Count: {count}") # Debug
        if not is_grayscale(avg_color):
            # Find a sample that matches that average color.
            try:
                most_common_index = average_colors.index(avg_color)
                tile = samples[most_common_index]
                #print(f" Found non-grayscale tile at index {most_common_index}") # Debug
                break
            except ValueError:
                print("Error: Color somehow not in list") # Keep this error message
                continue

    # 3. Default to a gray tile if no non-grayscale color is found
    if tile is None:
        #print(" No suitable non-grayscale tile found. Defaulting to gray.") # Debug
        gray_color = (128, 128, 128)  # Default gray
        if num_channels == 1:
            tile = np.full((tile_size, tile_size), gray_color[0], dtype=np.uint8)
        else:
            tile = np.full((tile_size, tile_size, num_channels), gray_color, dtype=np.uint8)


    # 4. Blur the tile
    #print("4. Blurring the tile...") # Debug
    blurred_tile = cv2.GaussianBlur(tile, (15, 15), 0)
    #print(" Tile blurred.") # Debug

    # 5. Create the new image (filled with neutral gray)
    #print("5. Creating the new image...") # Debug
    new_height = height + 2 * border_width
    new_width = width + 2 * border_width
    if num_channels == 1:
        framed_image = np.full((new_height, new_width), 128, dtype=np.uint8)  # grayscale
    else:
        framed_image = np.full((new_height, new_width, num_channels), (128,128,128), dtype=np.uint8)
    #print(f" New image dimensions: {new_height}x{new_width}") # Debug

    # 6. Tile the border
    #print("6. Tiling the border...") # Debug
    for x in range(0, new_width, tile_size):
        framed_image[0:border_width, x:min(x + tile_size, new_width)] = blurred_tile[0:border_width, 0:min(tile_size, new_width - x)]
        framed_image[new_height - border_width:new_height, x:min(x + tile_size, new_width)] = blurred_tile[0:border_width, 0:min(tile_size, new_width - x)]

    # Left and Right borders
    for y in range(border_width, new_height - border_width, tile_size):
        framed_image[y:min(y + tile_size, new_height-border_width), 0:border_width] = blurred_tile[0:min(tile_size, new_height-border_width-y), 0:border_width]
        framed_image[y:min(y + tile_size, new_height-border_width), new_width - border_width:new_width] = blurred_tile[0:min(tile_size, new_height-border_width-y), 0:border_width]
    #print(" Border tiled.") # Debug

    # 7. Copy the original image
    #print("7. Copying the original image into the center...") # Debug
    framed_image[border_width:border_width + height, border_width:border_width + width] = image
    #print(" Image copied.") # Debug

    return framed_image

def process_image_tiles(image: np.ndarray, tile_size: int = 16) -> np.ndarray:
    """Processes an image in tiles, blacking out tiles that don't meet criteria."""
    height, width = image.shape[:2]
    mask = np.zeros_like(image)

    for y in range(0, height, tile_size):
        for x in range(0, width, tile_size):
            tile = image[y:min(y + tile_size, height), x:min(x + tile_size, width)]
            avg_color = np.mean(tile, axis=(0, 1))

            # Check the color criteria
            criteria_met = (
                (np.sum(avg_color > 100) >= 2) and  # At least two values > 100
                (np.max(avg_color) - np.min(avg_color) <= 24)  # All within 24 of each other
            )

            if criteria_met:
                mask[y:min(y + tile_size, height), x:min(x + tile_size, width)] = [255, 255, 255]  # white

    processed_image = np.where(mask == [255, 255, 255], image, 0)
    return processed_image

def order_points(points: list[list[int]]) -> list[list[int]]:
    """Rearrange coordinates to order: top-left, top-right, bottom-right, bottom-left."""
    rect = np.zeros((4, 2), dtype='float32')
    points = np.array(points)
    s = points.sum(axis=1)
    rect[0] = points[np.argmin(s)]  # Top-left
    rect[2] = points[np.argmax(s)]  # Bottom-right

    diff = np.diff(points, axis=1)
    rect[1] = points[np.argmin(diff)]  # Top-right
    rect[3] = points[np.argmax(diff)]  # Bottom-left
    return rect.astype('int').tolist()

def find_largest_quadrilateral(contours: list) -> Optional[np.ndarray]:
    """Finds the largest quadrilateral contour."""
    largest_quadrilateral = None
    max_area = 0
    for cnt in contours:
        epsilon = 0.02 * cv2.arcLength(cnt, True)
        approx = cv2.approxPolyDP(cnt, epsilon, True)
        if len(approx) == 4:
            area = cv2.contourArea(approx)
            if area > max_area:
                max_area = area
                largest_quadrilateral = approx
    return largest_quadrilateral

def enlarge_quadrilateral(quadrilateral: np.ndarray, image_shape: tuple[int, int], enlargement_factor: float = 1.25, margin: int = 128) -> np.ndarray:
    """Enlarges the quadrilateral by a given factor, ensuring it stays within bounds."""
     # Calculate the center of the quadrilateral
    M = cv2.moments(quadrilateral)
    if M["m00"] != 0:
        cX = int(M["m10"] / M["m00"])
        cY = int(M["m01"] / M["m00"])
    else:
        cX = int(sum(p[0][0] for p in quadrilateral) / 4)
        cY = int(sum(p[0][1] for p in quadrilateral) / 4)

    enlarged_quad = []
    for point in quadrilateral:
        x, y = point[0]
        new_x = int(cX + enlargement_factor * (x - cX))
        new_y = int(cY + enlargement_factor * (y - cY))
        enlarged_quad.append([[new_x, new_y]])
    enlarged_quad = np.array(enlarged_quad, dtype=np.int32)
    
    height, width = image_shape
    for point in enlarged_quad:
        point[0][0] = max(margin, min(point[0][0], width - margin))
        point[0][1] = max(margin, min(point[0][1], height- margin))
    return enlarged_quad
    

def perspective_transform(image: np.ndarray, corners: list[list[int]]) -> tuple[np.ndarray, int, int]:
    """Applies perspective transform to the image."""
    # Order the corners
    ordered_corners = order_points(corners)
    (tl, tr, br, bl) = ordered_corners

    # Compute the width of the new image
    width_a = np.sqrt(((br[0] - bl[0]) ** 2) + ((br[1] - bl[1]) ** 2))
    width_b = np.sqrt(((tr[0] - tl[0]) ** 2) + ((tr[1] - tl[1]) ** 2))
    max_width = max(int(width_a), int(width_b))

    # Compute the height of the new image
    height_a = np.sqrt(((tr[0] - br[0]) ** 2) + ((tr[1] - br[1]) ** 2))
    height_b = np.sqrt(((tl[0] - bl[0]) ** 2) + ((tl[1] - bl[1]) ** 2))
    max_height = max(int(height_a), int(height_b))

    # Destination points
    dst_corners = np.array([
        [0, 0],
        [max_width - 1, 0],
        [max_width - 1, max_height - 1],
        [0, max_height - 1]], dtype="float32")

    # Compute the perspective transform matrix and apply it
    transform_matrix = cv2.getPerspectiveTransform(np.float32(ordered_corners), dst_corners)
    warped_image = cv2.warpPerspective(image, transform_matrix, (max_width, max_height))
    return warped_image, max_width, max_height

def detect_document(image: np.ndarray, min_dim_threshold: int = 500, dim_limit: int = 1440) -> np.ndarray:
    """Detects and extracts a document from an image."""
    
    # Resize image
    orig_image = image.copy()
    height, width = image.shape[:2]
    max_dim = max(height, width)
    useless = False
    if max_dim > dim_limit:
        resize_scale = dim_limit / max_dim
        image = cv2.resize(image, None, fx=resize_scale, fy=resize_scale)
    elif max_dim < min_dim_threshold:
        useless = True
      
    if useless:
        return orig_image
      
    framed_image = add_black_frame(image.copy())

    # Repeated Closing operation to remove text.
    kernel = np.ones((5, 5), np.uint8)
    closed_image = cv2.morphologyEx(image, cv2.MORPH_CLOSE, kernel, iterations=4)
    
    #create tiled border
    bordered_image = create_tiled_border(closed_image)

    # Process the image to remove noise
    processed_image = process_image_tiles(bordered_image)

    # GrabCut for foreground extraction
    mask = np.zeros(processed_image.shape[:2], np.uint8)
    bgd_model = np.zeros((1, 65), np.float64)
    fgd_model = np.zeros((1, 65), np.float64)
    rect = (20, 20, processed_image.shape[1] - 20, processed_image.shape[0] - 20)
    cv2.grabCut(processed_image, mask, rect, bgd_model, fgd_model, 5, cv2.GC_INIT_WITH_RECT)
    mask2 = np.where((mask == 2) | (mask == 0), 0, 1).astype('uint8')
    grabcut_image = processed_image * mask2[:, :, np.newaxis]

    # Edge Detection
    gray = cv2.cvtColor(grabcut_image, cv2.COLOR_BGR2GRAY)
    gray = cv2.GaussianBlur(gray, (11, 11), 0)
    canny = cv2.Canny(gray, 100, 200)
    canny = cv2.dilate(canny, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5)))

    # Finding contours
    contours, _ = cv2.findContours(canny, cv2.RETR_LIST, cv2.CHAIN_APPROX_NONE)
    page = sorted(contours, key=cv2.contourArea, reverse=True)[:5] # Keep top 5 largest

    # Find the largest quadrilateral
    largest_quad = find_largest_quadrilateral(page)
    
    if largest_quad is not None:
        #enlarge and refine
        largest_quad = enlarge_quadrilateral(largest_quad, framed_image.shape[:2])
            
        # Perspective transform
        final_image, width, height = perspective_transform(framed_image, largest_quad.reshape(4, 2))

        fullheight, fullwidth = framed_image.shape[:2]
        
        if height < 0.20 * fullheight or width < 0.20 * fullwidth:
            return orig_image
        return final_image
    else:
      return orig_image
        
def process_and_save_image(input_path: str, output_dir: str = ".") -> Optional[str]:
    """Processes the image, saves it with a UUID, and returns the output path.

    Args:
        input_path: Path to the input image.
        output_dir: Directory to save the output image (defaults to current directory).

    Returns:
        The full path to the saved output image, or None if processing failed.
    """
    try:
        image = cv2.imread(input_path, cv2.IMREAD_COLOR)
        if image is None:
            raise FileNotFoundError(f"Could not load image at {input_path}")

        final_image = detect_document(image)

        # Create output directory if it doesn't exist
        if not os.path.exists(output_dir):
            os.makedirs(output_dir)

        # Generate a unique filename using UUID
        output_filename = f"{uuid.uuid4()}.jpg"
        output_path = os.path.join(output_dir, output_filename)

        cv2.imwrite(output_path, final_image)
        #print(f"Image processing complete. Output saved as {output_path}") # Debug - now removed
        return output_path
    except FileNotFoundError as e:
        print(f"Error: {e}")
        return None
    except Exception as e:
        print(f"An unexpected error occurred: {e}")
        return None
    
