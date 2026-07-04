
import os
from PIL import Image

def slice_sprites(sprite_path, output_dir):
    try:
        img = Image.open(sprite_path)
        width, height = img.size
        # Assume 100x100 tiles
        tile_width = 100
        tile_height = 100
        
        # Order of pieces: I, O, T, J, L, S, Z, Border
        # Based on index logic: 
        # 0: I (Cyan)
        # 1: O (Yellow)
        # 2: T (Magenta)
        # 3: J (Blue)
        # 4: L (Orange)
        # 5: S (Green)
        # 6: Z (Red)
        # 7: Border
        
        piece_names = ['i', 'o', 't', 'j', 'l', 's', 'z', 'border']
        
        for i, name in enumerate(piece_names):
            left = i * tile_width
            right = left + tile_width
            upper = 0
            lower = tile_height
            
            if right <= width:
                tile = img.crop((left, upper, right, lower))
                output_path = os.path.join(output_dir, f"{name}.png")
                tile.save(output_path)
                print(f"Saved {output_path}")
            else:
                print(f"Warning: Not enough width for tile {i} ({name})")
                
    except Exception as e:
        print(f"Error processing {sprite_path}: {e}")

def copy_background(bg_path, output_dir):
    try:
        img = Image.open(bg_path)
        output_path = os.path.join(output_dir, "background.png")
        img.save(output_path)
        print(f"Saved background to {output_path}")
    except Exception as e:
        print(f"Error copying background {bg_path}: {e}")

def create_button_from_border(output_dir):
    try:
        # Use the border tile as button tile
        border_path = os.path.join(output_dir, "border.png")
        if os.path.exists(border_path):
            img = Image.open(border_path)
            button_path = os.path.join(output_dir, "button.png")
            img.save(button_path)
            print(f"Created button from border: {button_path}")
    except Exception as e:
        print(f"Error creating button: {e}")

# Paths
base_dir = r"c:\Users\Crisrueda99\AndroidStudioProjects\BlocksTry"
drawable_dir = os.path.join(base_dir, "shared", "src", "main", "res", "drawable")
assets_dir = os.path.join(base_dir, "app", "src", "main", "assets", "themes")

# Create output directories if they don't exist
os.makedirs(os.path.join(assets_dir, "invierno"), exist_ok=True)
os.makedirs(os.path.join(assets_dir, "egipto"), exist_ok=True)

# Winter
winter_sprite = os.path.join(drawable_dir, "winter_tiles.png")
winter_bg = os.path.join(drawable_dir, "winter_background.png")
winter_out = os.path.join(assets_dir, "invierno")
print("Processing Winter theme...")
slice_sprites(winter_sprite, winter_out)
copy_background(winter_bg, winter_out)
create_button_from_border(winter_out)

# Egypt
egypt_sprite = os.path.join(drawable_dir, "egypt_tiles.png")
egypt_bg = os.path.join(drawable_dir, "egypt_background.png")
egypt_out = os.path.join(assets_dir, "egipto")
print("\nProcessing Egypt theme...")
slice_sprites(egypt_sprite, egypt_out)
copy_background(egypt_bg, egypt_out)
create_button_from_border(egypt_out)

print("\nDone! All theme assets have been extracted.")
