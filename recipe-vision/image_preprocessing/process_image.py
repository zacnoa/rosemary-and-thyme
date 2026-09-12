from typing import BinaryIO

import cv2
import numpy as np


def process_image(image_file: BinaryIO):

    # Reads the image as bytes
    content = image_file.read()
    if not content:
        raise ValueError("The image file is empty.")
    # Converts the bytes to  unsigned 8-bit integer array
    nparr = np.frombuffer(content, np.uint8)

    # Decodes the image from JPEG/PNG format to BGR
    img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("The supplied file is not a supported image.")

    # Converts recipe image to grayscale. We do not need color information for text extraction.
    grey = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

    # Values that are lower or equal than the threshold are set to 0 while those that are greater to 255.
    # But _INV inverts it so the result is that the background is black and the text is white.
    # Usually we would need to define the threshold ourselves
    # ('0' argument but here we use the THRESH_OTSU which finds the threshold by looking at the spread of the pixel intensity )
    _, thresh_image = cv2.threshold(
        grey, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU
    )

    # np.where returns two arrays, each containing x or y coordinates respectfully
    # columnn stack pairs them together into one array with pairs
    coordinates = np.column_stack(np.where(thresh_image > 0))

    # Finds the minimum area rectangle that contains these coordinates and returns center,(width,height) and angle.
    # We are only interested in the angle so we use [-1]
    angle = cv2.minAreaRect(coordinates)[-1]

    # Normalized angle
    if angle < -45:
        angle = -(90 + angle)
    else:
        angle = -angle

    h, w = img.shape[:2]
    center = (w // 2, h // 2)

    # Returns the calculation for rotating the image
    rotation_matrix = cv2.getRotationMatrix2D(center, angle, 1.0)

    # Does the rotation while filling the new pixels with the border pixels i.e white in most cases
    rotated = cv2.warpAffine(
        img,
        rotation_matrix,
        (w, h),
        flags=cv2.INTER_CUBIC,
        borderMode=cv2.BORDER_CONSTANT,
        borderValue=(255, 255, 255),
    )

    grey_rotated = cv2.cvtColor(rotated, cv2.COLOR_BGR2GRAY)

    _, grey_thresh = cv2.threshold(
        grey_rotated, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU
    )

    return img, rotated, grey_thresh
