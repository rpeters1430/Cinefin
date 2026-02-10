from PIL import Image
import os

def make_square_and_resize(input_path, output_path, final_size=432):
    """
    Make an image square by center-cropping, then resize to target size.
    Adds proper padding for adaptive icon safe zone.
    """
    img = Image.open(input_path)

    # Convert to RGBA if needed
    if img.mode != 'RGBA':
        img = img.convert('RGBA')

    width, height = img.size

    # Center crop to square
    if width != height:
        size = min(width, height)
        left = (width - size) // 2
        top = (height - size) // 2
        img = img.crop((left, top, left + size, top + size))

    # Trim transparency to get just the icon content
    bbox = img.getbbox()
    if bbox:
        img = img.crop(bbox)

    # Add padding for adaptive icon safe zone
    # Adaptive icons: 108dp total, safe zone is inner 66dp (61% of total)
    # Icon should be ~65% of canvas to stay in safe zone
    w, h = img.size
    icon_size = max(w, h)
    canvas_size = int(icon_size / 0.65)

    # Create transparent canvas
    canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))

    # Paste icon in center
    paste_x = (canvas_size - w) // 2
    paste_y = (canvas_size - h) // 2
    canvas.paste(img, (paste_x, paste_y), img)

    # Resize to final size
    final_img = canvas.resize((final_size, final_size), Image.Resampling.LANCZOS)
    final_img.save(output_path)
    print(f"[OK] Created {os.path.basename(output_path)} ({final_size}x{final_size})")

def process_background(input_path, output_path, final_size=432):
    """Process background - can be solid color or image."""
    img = Image.open(input_path)

    # Convert to RGB (backgrounds don't need transparency)
    if img.mode == 'RGBA':
        # Create white background
        bg = Image.new('RGB', img.size, (255, 255, 255))
        bg.paste(img, mask=img.split()[3] if img.mode == 'RGBA' else None)
        img = bg
    elif img.mode != 'RGB':
        img = img.convert('RGB')

    # Resize to square if needed
    if img.size[0] != img.size[1]:
        size = max(img.size)
        squared = Image.new('RGB', (size, size), (255, 255, 255))
        paste_x = (size - img.size[0]) // 2
        paste_y = (size - img.size[1]) // 2
        squared.paste(img, (paste_x, paste_y))
        img = squared

    # Resize to final size
    final_img = img.resize((final_size, final_size), Image.Resampling.LANCZOS)
    final_img.save(output_path)
    print(f"[OK] Created {os.path.basename(output_path)} ({final_size}x{final_size})")

def create_all_densities(xxxhdpi_dir, output_base_dir):
    """Create all Android density versions from xxxhdpi sources."""
    densities = {
        "mdpi": 108,
        "hdpi": 162,
        "xhdpi": 216,
        "xxhdpi": 324,
    }

    icon_files = [
        "ic_launcher_foreground.png",
        "ic_launcher_background.png",
        "ic_launcher_monochrome.png"
    ]

    for icon_file in icon_files:
        source_path = os.path.join(xxxhdpi_dir, icon_file)
        if not os.path.exists(source_path):
            print(f"[WARN] {icon_file} not found, skipping")
            continue

        source_img = Image.open(source_path)

        for density, size in densities.items():
            output_dir = os.path.join(output_base_dir, f"mipmap-{density}")
            os.makedirs(output_dir, exist_ok=True)

            resized = source_img.resize((size, size), Image.Resampling.LANCZOS)
            output_path = os.path.join(output_dir, icon_file)
            resized.save(output_path)

        print(f"[OK] Created {icon_file} for all densities (mdpi through xxhdpi)")

def main():
    # Output directory
    output_dir = "app/src/main/res/mipmap-xxxhdpi"
    os.makedirs(output_dir, exist_ok=True)

    print("=" * 60)
    print("Processing Android Adaptive Icons")
    print("=" * 60)

    # Process foreground (may be non-square)
    print("\n1. Processing foreground...")
    if os.path.exists("foreground.png"):
        make_square_and_resize(
            "foreground.png",
            os.path.join(output_dir, "ic_launcher_foreground.png"),
            432
        )
    else:
        print("[WARN] foreground.png not found!")

    # Process background
    print("\n2. Processing background...")
    if os.path.exists("background.png"):
        process_background(
            "background.png",
            os.path.join(output_dir, "ic_launcher_background.png"),
            432
        )
    else:
        print("[WARN] background.png not found!")

    # Process monochrome (may be non-square)
    print("\n3. Processing monochrome...")
    if os.path.exists("mono.png"):
        make_square_and_resize(
            "mono.png",
            os.path.join(output_dir, "ic_launcher_monochrome.png"),
            432
        )
    else:
        print("[WARN] mono.png not found!")

    # Create all density versions
    print("\n4. Creating all density versions...")
    create_all_densities(output_dir, "app/src/main/res")

    print("\n" + "=" * 60)
    print("Done! Icons created successfully.")
    print("=" * 60)
    print("\nNext steps:")
    print("1. Add monochrome layer back to XML files")
    print("2. Build and test: ./gradlew.bat assembleDebug")
    print("3. Install and check launcher icons")

if __name__ == "__main__":
    main()
