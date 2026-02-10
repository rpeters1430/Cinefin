from PIL import Image
import os

def split_and_save(input_path, output_dir, is_transparent=True):
    """
    Split a 3-column icon sheet into individual icons.

    Args:
        input_path: Path to the source PNG with 3 icons
        output_dir: Directory to save the individual icons
        is_transparent: True for transparent background file, False for white background file
    """
    img = Image.open(input_path)
    width, height = img.size

    col_width = width // 3

    # Crop the top 65% of the image to avoid text labels at bottom
    crop_height = int(height * 0.65)

    def extract_icon(col_index, name):
        left = col_index * col_width
        right = (col_index + 1) * col_width

        # Crop the column top part
        icon_col = img.crop((left, 0, right, crop_height))

        # Trim transparency to get just the icon
        bbox = icon_col.getbbox()
        if not bbox:
            print(f"Warning: No content found for {name}")
            return

        icon_img = icon_col.crop(bbox)

        # Make square and add padding for adaptive icon safe zone
        # Adaptive icons are 108dp, safe zone is 66-72dp
        # Make the icon roughly 65% of the total size
        w, h = icon_img.size
        size = max(w, h)
        final_size = int(size / 0.65)

        final_img = Image.new("RGBA", (final_size, final_size), (0, 0, 0, 0))
        final_img.paste(icon_img, ((final_size - w) // 2, (final_size - h) // 2), icon_img if icon_img.mode == 'RGBA' else None)

        # Resize to 432x432 (XXXHDPI = 108dp × 4)
        final_img = final_img.resize((432, 432), Image.Resampling.LANCZOS)
        final_img.save(os.path.join(output_dir, name))
        print(f"Created {name} (432x432)")

    if is_transparent:
        # Foreground is the first column
        extract_icon(0, "ic_launcher_foreground.png")

        # Monochrome is the third column (Material You icon)
        extract_icon(2, "ic_launcher_monochrome.png")
    else:
        # White background file - extract the background color
        # Sample color from the middle of the second column
        bg_col_center_x = col_width + (col_width // 2)
        bg_col_center_y = crop_height // 2
        bg_color = img.getpixel((bg_col_center_x, bg_col_center_y))

        # Create a solid background
        bg_final = Image.new("RGB", (432, 432), bg_color[:3] if len(bg_color) > 3 else bg_color)
        bg_final.save(os.path.join(output_dir, "ic_launcher_background.png"))
        print(f"Created ic_launcher_background.png (432x432, color: {bg_color})")

def create_all_density_icons(xxxhdpi_dir, output_base_dir):
    """
    Create all density versions from XXXHDPI source icons.

    Android icon densities (for 108dp adaptive icon):
    - MDPI: 108px (1x)
    - HDPI: 162px (1.5x)
    - XHDPI: 216px (2x)
    - XXHDPI: 324px (3x)
    - XXXHDPI: 432px (4x)
    """
    densities = {
        "mdpi": 108,
        "hdpi": 162,
        "xhdpi": 216,
        "xxhdpi": 324,
    }

    icon_names = [
        "ic_launcher_foreground.png",
        "ic_launcher_background.png",
        "ic_launcher_monochrome.png"
    ]

    for icon_name in icon_names:
        source_path = os.path.join(xxxhdpi_dir, icon_name)
        if not os.path.exists(source_path):
            print(f"Warning: {icon_name} not found, skipping")
            continue

        source_img = Image.open(source_path)

        for density, size in densities.items():
            output_dir = os.path.join(output_base_dir, f"mipmap-{density}")
            os.makedirs(output_dir, exist_ok=True)

            resized = source_img.resize((size, size), Image.Resampling.LANCZOS)
            output_path = os.path.join(output_dir, icon_name)
            resized.save(output_path)
            print(f"Created {density}/{icon_name} ({size}x{size})")

if __name__ == "__main__":
    # Output directory
    output_dir = "app/src/main/res/mipmap-xxxhdpi"
    os.makedirs(output_dir, exist_ok=True)

    print("Splitting transparent background file...")
    split_and_save("icons 3 transparent.png", output_dir, is_transparent=True)

    print("\nExtracting background color from white background file...")
    split_and_save("icons 3 white background.png", output_dir, is_transparent=False)

    print("\nCreating all density versions...")
    create_all_density_icons(output_dir, "app/src/main/res")

    print("\n✅ Done! Icon files created in app/src/main/res/mipmap-*")
    print("\nNext steps:")
    print("1. Verify the icons look correct in mipmap-xxxhdpi/")
    print("2. Update ic_launcher.xml and ic_launcher_round.xml to reference these files")
    print("3. Remove old .webp files if no longer needed")
