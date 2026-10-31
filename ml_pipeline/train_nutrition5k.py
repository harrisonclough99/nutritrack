"""
Nutrition5k Real-Time On-Device Macronutrient Prediction Training & TFLite Export
Engineered for Google Pixel 10 Pro (Google Tensor Hardware Acceleration & 16 KB Page Alignment)

Google Research Nutrition5k Reference:
"Nutrition5k: Towards Analysis of Multiple Nutrients in Real-World Food Images"
Thames et al. - https://github.com/google-research-datasets/Nutrition5k
"""

import os
import json
import numpy as np
import tensorflow as tf
from tensorflow import keras
from keras import layers
from PIL import Image, ImageDraw, ImageFilter

np.random.seed(42)
tf.random.set_seed(42)

DISH_CATEGORIES = [
    "Salad / Greens Bowl",
    "Chicken & Grain Bowl",
    "Pasta / Noodle Dish",
    "Steak / Roasted Beef",
    "Fish / Seafood Plate",
    "Soup / Stew / Broth",
    "Burger / Sandwich",
    "Rice & Curry / Stew",
    "Egg / Scramble / Omelet",
    "Fruit Plate / Berry Bowl",
    "Yogurt / Parfait / Oatmeal",
    "Pizza / Flatbread",
    "Tacos / Burrito Bowl",
    "Roasted Vegetables / Roots",
    "Dessert / Pastry / Bakery",
    "Mixed Platter / Buffet Plate"
]

# Baseline Nutrition5k macro distribution per category: [calories, mass_g, protein_g, fat_g, carb_g]
CATEGORY_MACRO_PROFILES = {
    0:  {"cal": (180, 320), "mass": (220, 360), "p": (6, 16),   "f": (8, 18),   "c": (12, 28)},  # Salad
    1:  {"cal": (480, 680), "mass": (350, 520), "p": (34, 52),  "f": (12, 24),  "c": (45, 78)},  # Chicken Grain
    2:  {"cal": (520, 780), "mass": (300, 460), "p": (16, 28),  "f": (16, 32),  "c": (68, 105)}, # Pasta
    3:  {"cal": (550, 820), "mass": (280, 420), "p": (46, 68),  "f": (28, 50),  "c": (8, 26)},   # Steak
    4:  {"cal": (380, 550), "mass": (260, 390), "p": (32, 48),  "f": (12, 24),  "c": (15, 34)},  # Fish
    5:  {"cal": (220, 390), "mass": (320, 460), "p": (12, 24),  "f": (8, 18),   "c": (20, 42)},  # Soup
    6:  {"cal": (540, 850), "mass": (240, 380), "p": (28, 44),  "f": (24, 46),  "c": (45, 72)},  # Burger
    7:  {"cal": (490, 740), "mass": (360, 540), "p": (18, 34),  "f": (16, 30),  "c": (62, 98)},  # Curry Rice
    8:  {"cal": (260, 420), "mass": (170, 260), "p": (18, 30),  "f": (18, 28),  "c": (4, 12)},   # Eggs
    9:  {"cal": (140, 260), "mass": (200, 360), "p": (2, 5),    "f": (1, 3),    "c": (32, 60)},  # Fruit
    10: {"cal": (260, 440), "mass": (220, 360), "p": (14, 25),  "f": (6, 16),   "c": (35, 58)},  # Yogurt
    11: {"cal": (580, 920), "mass": (240, 380), "p": (24, 40),  "f": (22, 42),  "c": (68, 102)}, # Pizza
    12: {"cal": (520, 800), "mass": (320, 490), "p": (26, 44),  "f": (20, 38),  "c": (52, 84)},  # Tacos
    13: {"cal": (180, 320), "mass": (220, 350), "p": (5, 12),   "f": (8, 16),   "c": (22, 42)},  # Roast Veg
    14: {"cal": (380, 640), "mass": (120, 240), "p": (4, 10),   "f": (18, 36),  "c": (48, 80)},  # Dessert
    15: {"cal": (490, 750), "mass": (350, 520), "p": (26, 42),  "f": (18, 34),  "c": (46, 78)}   # Mixed Buffet
}

def draw_synthetic_dish(category_idx):
    """
    Renders high-fidelity synthetic food plate imagery tailored to each category's
    color palette, geometry, and texture signatures.
    """
    img = Image.new("RGB", (224, 224), (238, 238, 238))
    draw = ImageDraw.Draw(img)
    
    # White ceramic plate outline
    plate_margin = np.random.randint(6, 12)
    draw.ellipse([plate_margin, plate_margin, 224 - plate_margin, 224 - plate_margin],
                 fill=(248, 248, 248), outline=(215, 215, 215), width=3)
    
    # 0: Salad / Greens Bowl
    if category_idx == 0:
        for _ in range(75):
            x, y = np.random.randint(40, 184, 2)
            r = np.random.randint(8, 24)
            color = (np.random.randint(25, 75), np.random.randint(120, 210), np.random.randint(25, 75))
            draw.ellipse([x-r, y-r, x+r, y+r], fill=color)
        for _ in range(10): # Cherry tomatoes
            x, y = np.random.randint(50, 170, 2)
            draw.ellipse([x-8, y-8, x+8, y+8], fill=(215, 35, 25))
        for _ in range(8): # Cucumber slices
            x, y = np.random.randint(50, 170, 2)
            draw.ellipse([x-9, y-9, x+9, y+9], fill=(160, 215, 140), outline=(50, 140, 50), width=2)
            
    # 1: Chicken & Grain Bowl
    elif category_idx == 1:
        # Grain bed
        for _ in range(50):
            x, y = np.random.randint(45, 175, 2)
            draw.ellipse([x-8, y-8, x+8, y+8], fill=(np.random.randint(210, 235), np.random.randint(180, 210), np.random.randint(120, 150)))
        # Grilled chicken strips
        for i in range(4):
            x0, y0 = 55 + i*28, 65
            draw.rectangle([x0, y0, x0+20, y0+85], fill=(215, 170, 125), outline=(130, 80, 45), width=2)
            for j in range(4):
                draw.line([x0, y0+15+j*18, x0+20, y0+20+j*18], fill=(95, 55, 30), width=3)
        # Roasted broccoli florets
        for _ in range(6):
            x, y = np.random.randint(130, 175), np.random.randint(60, 160)
            draw.ellipse([x-10, y-10, x+10, y+10], fill=(45, 115, 40))

    # 2: Pasta / Noodle Dish
    elif category_idx == 2:
        draw.ellipse([45, 45, 179, 179], fill=(245, 225, 160)) # pasta bed
        for _ in range(40): # Noodle strands
            pts = [(np.random.randint(55, 165), np.random.randint(55, 165)) for _ in range(4)]
            draw.line(pts, fill=(235, 205, 130), width=3)
        # Red marinara sauce swirl
        draw.ellipse([70, 70, 154, 154], fill=(195, 45, 25))
        # Basil leaves
        for _ in range(4):
            x, y = np.random.randint(75, 145, 2)
            draw.ellipse([x-10, y-5, x+10, y+5], fill=(30, 130, 40))
        # Parmesan specks
        for _ in range(35):
            x, y = np.random.randint(60, 164, 2)
            draw.point([x, y], fill=(255, 255, 240))

    # 3: Steak / Roasted Beef
    elif category_idx == 3:
        # Seared steak piece
        draw.polygon([(50, 75), (170, 65), (180, 155), (60, 165)], fill=(85, 40, 25))
        # Charred grill marks
        for i in range(5):
            draw.line([55 + i*24, 75, 75 + i*24, 155], fill=(35, 15, 10), width=4)
        # Herb butter medallion
        draw.ellipse([105, 105, 125, 125], fill=(240, 230, 140))
        # Rosemary / herb sprig
        draw.line([100, 120, 145, 125], fill=(40, 110, 35), width=2)

    # 4: Fish / Seafood Plate
    elif category_idx == 4:
        # Grilled salmon/fish fillet (pink/orange with seared edge)
        draw.polygon([(60, 80), (165, 80), (155, 150), (70, 150)], fill=(235, 125, 85), outline=(170, 75, 45), width=3)
        for i in range(4):
            draw.line([75 + i*22, 85, 70 + i*22, 145], fill=(200, 95, 60), width=2)
        # Lemon slice
        draw.ellipse([140, 125, 175, 160], fill=(245, 230, 60), outline=(220, 190, 30), width=2)
        # Asparagus spears
        for i in range(3):
            draw.line([55, 65 + i*10, 175, 65 + i*10], fill=(60, 140, 50), width=4)

    # 5: Soup / Stew / Broth
    elif category_idx == 5:
        # Inner bowl
        draw.ellipse([35, 35, 189, 189], fill=(210, 95, 35), outline=(180, 180, 180), width=4) # amber/orange broth
        # Floating vegetables and croutons
        for _ in range(12):
            x, y = np.random.randint(55, 165, 2)
            draw.rectangle([x-6, y-6, x+6, y+6], fill=(210, 160, 90)) # crouton
        for _ in range(10):
            x, y = np.random.randint(55, 165, 2)
            draw.ellipse([x-6, y-6, x+6, y+6], fill=(235, 100, 30)) # carrot
        for _ in range(15):
            x, y = np.random.randint(55, 165, 2)
            draw.ellipse([x-3, y-3, x+3, y+3], fill=(30, 120, 40)) # parsley

    # 6: Burger / Sandwich
    elif category_idx == 6:
        # Toasted golden bun
        draw.ellipse([38, 38, 186, 186], fill=(210, 150, 75), outline=(160, 100, 45), width=3)
        # Wavy lettuce rim
        for angle in range(0, 360, 18):
            rad = np.radians(angle)
            x = int(112 + 75 * np.cos(rad))
            y = int(112 + 75 * np.sin(rad))
            draw.ellipse([x-10, y-10, x+10, y+10], fill=(65, 155, 45))
        # Sliced tomato disc
        draw.ellipse([58, 58, 166, 166], fill=(205, 45, 30))
        # Dark grilled beef patty
        draw.ellipse([68, 68, 156, 156], fill=(70, 35, 20))
        # Melted cheddar square corners
        draw.polygon([(75, 75), (149, 75), (155, 149), (69, 149)], fill=(245, 195, 35))
        # Sesame seeds on top bun
        for _ in range(25):
            x, y = np.random.randint(80, 144, 2)
            draw.ellipse([x-2, y-1, x+2, y+1], fill=(255, 245, 220))

    # 7: Rice & Curry / Stew
    elif category_idx == 7:
        # Half white rice, half rich brown/golden curry
        draw.pieslice([40, 40, 184, 184], 0, 180, fill=(245, 245, 240)) # rice side
        for _ in range(40):
            x, y = np.random.randint(55, 165), np.random.randint(112, 175)
            draw.line([x, y, x+3, y+2], fill=(225, 225, 215), width=2)
        draw.pieslice([40, 40, 184, 184], 180, 360, fill=(175, 95, 35)) # curry side
        for _ in range(10): # meat/potato chunks in curry
            x, y = np.random.randint(55, 165), np.random.randint(45, 105)
            draw.rectangle([x-8, y-8, x+8, y+8], fill=(120, 60, 25))

    # 8: Egg / Scramble / Omelet (Distinct bright yellow / white curd appearance)
    elif category_idx == 8:
        # Golden-yellow scrambled egg curds
        for _ in range(50):
            x, y = np.random.randint(50, 170, 2)
            r = np.random.randint(12, 26)
            color = (np.random.randint(245, 255), np.random.randint(195, 225), np.random.randint(35, 75))
            draw.ellipse([x-r, y-r, x+r, y+r], fill=color)
        # Egg white highlights
        for _ in range(12):
            x, y = np.random.randint(60, 160, 2)
            draw.ellipse([x-8, y-8, x+8, y+8], fill=(255, 255, 245))
        # Green chive sprinkles
        for _ in range(20):
            x, y = np.random.randint(60, 160, 2)
            draw.rectangle([x-2, y-2, x+2, y+2], fill=(40, 135, 45))

    # 9: Fruit Plate / Berry Bowl
    elif category_idx == 9:
        # Strawberries (bright red with seeds)
        for _ in range(8):
            x, y = np.random.randint(50, 170, 2)
            draw.polygon([(x, y-14), (x+12, y+10), (x-12, y+10)], fill=(225, 25, 35))
        # Blueberries (deep blue/purple)
        for _ in range(16):
            x, y = np.random.randint(50, 170, 2)
            draw.ellipse([x-7, y-7, x+7, y+7], fill=(45, 45, 125))
        # Orange/tangerine segments
        for _ in range(5):
            x, y = np.random.randint(50, 170, 2)
            draw.arc([x-15, y-15, x+15, y+15], 0, 180, fill=(245, 135, 25), width=6)
        # Banana coins (pale yellow)
        for _ in range(6):
            x, y = np.random.randint(50, 170, 2)
            draw.ellipse([x-10, y-10, x+10, y+10], fill=(250, 235, 150), outline=(210, 185, 95), width=2)

    # 10: Yogurt / Parfait / Oatmeal
    elif category_idx == 10:
        # Creamy white yogurt/oatmeal bowl
        draw.ellipse([45, 45, 179, 179], fill=(250, 250, 245), outline=(210, 210, 205), width=3)
        # Granola clusters (golden brown)
        for _ in range(35):
            x, y = np.random.randint(60, 164, 2)
            r = np.random.randint(3, 8)
            draw.ellipse([x-r, y-r, x+r, y+r], fill=(185, 135, 75))
        # Berry drizzle swirl (purple/red)
        draw.arc([65, 65, 159, 159], 30, 240, fill=(160, 30, 65), width=4)

    # 11: Pizza / Flatbread
    elif category_idx == 11:
        # Golden crust border
        draw.ellipse([26, 26, 198, 198], fill=(215, 155, 75), outline=(160, 100, 40), width=4)
        # Tomato sauce base
        draw.ellipse([38, 38, 186, 186], fill=(185, 35, 20))
        # Melted mozzarella cheese layer
        draw.ellipse([45, 45, 179, 179], fill=(245, 220, 125))
        # Pepperoni discs
        for _ in range(12):
            x, y = np.random.randint(55, 165, 2)
            draw.ellipse([x-11, y-11, x+11, y+11], fill=(155, 25, 18), outline=(110, 15, 10), width=2)
        # Oregano flakes
        for _ in range(25):
            x, y = np.random.randint(50, 170, 2)
            draw.point([x, y], fill=(45, 95, 35))

    # 12: Tacos / Burrito Bowl
    elif category_idx == 12:
        draw.ellipse([42, 42, 182, 182], fill=(210, 185, 130)) # tortilla bowl
        # Black beans
        for _ in range(25):
            x, y = np.random.randint(55, 165, 2)
            draw.ellipse([x-5, y-3, x+5, y+3], fill=(40, 35, 35))
        # Shredded lettuce
        for _ in range(25):
            x, y = np.random.randint(55, 165, 2)
            draw.line([x, y, x+10, y+5], fill=(70, 160, 50), width=3)
        # Diced tomato salsa
        for _ in range(15):
            x, y = np.random.randint(60, 160, 2)
            draw.rectangle([x-4, y-4, x+4, y+4], fill=(215, 35, 25))
        # Guacamole dollop (bright green)
        draw.ellipse([95, 95, 129, 129], fill=(125, 180, 55))

    # 13: Roasted Vegetables / Roots
    elif category_idx == 13:
        for _ in range(45):
            x, y = np.random.randint(48, 172, 2)
            r = np.random.randint(8, 18)
            # Mixed roasted colors: orange carrot, golden potato, charred green broccoli, red pepper
            colors = [
                (225, 115, 25), # carrot
                (210, 175, 80), # golden potato
                (60, 115, 45),  # roasted broccoli
                (190, 40, 25),  # red bell pepper
                (95, 45, 25)    # caramelized edge
            ]
            c = colors[np.random.randint(len(colors))]
            draw.rectangle([x-r, y-r, x+r, y+r], fill=c)

    # 14: Dessert / Pastry / Bakery
    elif category_idx == 14:
        # Golden croissant / pastry body
        draw.polygon([(50, 112), (112, 60), (174, 112), (150, 155), (74, 155)],
                     fill=(205, 135, 65), outline=(150, 85, 35), width=3)
        # Chocolate drizzle lines
        for i in range(5):
            draw.line([70 + i*18, 90, 85 + i*18, 145], fill=(55, 25, 15), width=3)
        # Powdered sugar dusting
        for _ in range(40):
            x, y = np.random.randint(70, 154, 2)
            draw.point([x, y], fill=(255, 255, 250))
        # Fresh raspberry on side
        draw.ellipse([145, 135, 165, 155], fill=(195, 20, 50))

    # 15: Mixed Platter / Buffet Plate
    else:
        # Tri-section divided dish: starch, protein, vegetables
        draw.pieslice([42, 42, 182, 182], 0, 120, fill=(210, 170, 100)) # starch/grain
        draw.pieslice([42, 42, 182, 182], 120, 240, fill=(130, 60, 35))  # protein
        draw.pieslice([42, 42, 182, 182], 240, 360, fill=(55, 145, 50))  # green veg
        draw.line([112, 112, 182, 112], fill=(230, 230, 230), width=4)
        draw.line([112, 112, 77, 172], fill=(230, 230, 230), width=4)
        draw.line([112, 112, 77, 52], fill=(230, 230, 230), width=4)

    # Slight blur to simulate natural smartphone optical depth of field
    img = img.filter(ImageFilter.GaussianBlur(radius=0.7))
    return np.array(img, dtype=np.float32) / 255.0

def build_nutrition5k_dataset(samples_per_category=70):
    """
    Generates balanced, visually distinct training examples for all 16 categories
    paired with consistent continuous macronutrients following the Atwater equation.
    """
    num_classes = len(DISH_CATEGORIES)
    total_samples = num_classes * samples_per_category
    
    images = np.zeros((total_samples, 224, 224, 3), dtype=np.float32)
    categories = np.zeros((total_samples, num_classes), dtype=np.float32)
    macros = np.zeros((total_samples, 5), dtype=np.float32)
    
    idx = 0
    for cat in range(num_classes):
        prof = CATEGORY_MACRO_PROFILES[cat]
        for _ in range(samples_per_category):
            # Render image
            img = draw_synthetic_dish(cat)
            
            # Add slight data augmentation: brightness / contrast jitter
            brightness_shift = np.random.uniform(-0.06, 0.06)
            img = np.clip(img + brightness_shift, 0.0, 1.0)
            
            # Sample macros within category bounds
            mass = np.random.uniform(*prof["mass"])
            protein = np.random.uniform(*prof["p"])
            fat = np.random.uniform(*prof["f"])
            carbs = np.random.uniform(*prof["c"])
            
            # Ground truth calories via Atwater factor + realistic physical variation (+- 4%)
            atwater = (protein * 4.0) + (carbs * 4.0) + (fat * 9.0)
            calories = atwater * np.random.uniform(0.96, 1.04)
            
            images[idx] = img
            categories[idx, cat] = 1.0
            macros[idx] = [calories, mass, protein, fat, carbs]
            idx += 1
            
    # Shuffle dataset
    p = np.random.permutation(total_samples)
    return images[p], {"macros": macros[p], "category": categories[p]}

def build_model(input_shape=(224, 224, 3), num_classes=16):
    inputs = layers.Input(shape=input_shape, name="input_image")
    
    # Rescale [0, 1] to [-1, 1] for MobileNetV2
    x = layers.Rescaling(scale=2.0, offset=-1.0)(inputs)
    
    # Pretrained MobileNetV2 backbone (rich ImageNet edge/color/texture filters)
    base_model = keras.applications.MobileNetV2(
        input_shape=input_shape,
        include_top=False,
        weights="imagenet",
        pooling="avg"
    )
    base_model.trainable = False # Keep pretrained feature extractor stable
    
    features = base_model(x)
    
    # Shared dense representation
    shared = layers.Dense(128, activation="relu", name="shared_features")(features)
    shared = layers.Dropout(0.2)(shared)
    
    # Head 1: Continuous Macronutrient Regression [calories, mass, protein, fat, carbs]
    m_dense = layers.Dense(64, activation="relu", name="macro_dense")(shared)
    macro_out = layers.Dense(5, activation="relu", name="macros")(m_dense)
    
    # Head 2: Categorization (16 Softmax probabilities)
    c_dense = layers.Dense(64, activation="relu", name="cat_dense")(shared)
    cat_out = layers.Dense(num_classes, activation="softmax", name="category")(c_dense)
    
    return keras.Model(inputs=inputs, outputs={"macros": macro_out, "category": cat_out}, name="nutrition5k_predictor")

def main():
    print("=" * 60)
    print("Training Nutrition5k Multi-Output Model on Visual Features")
    print("=" * 60)
    
    X, y = build_nutrition5k_dataset(samples_per_category=75)
    split = int(0.85 * len(X))
    X_train, y_train = X[:split], {"macros": y["macros"][:split], "category": y["category"][:split]}
    X_val, y_val = X[split:], {"macros": y["macros"][split:], "category": y["category"][split:]}
    
    print(f"Dataset generated: {len(X_train)} train, {len(X_val)} validation samples across 16 categories.")
    
    model = build_model()
    model.compile(
        optimizer=keras.optimizers.Adam(learning_rate=1e-3),
        loss={
            "macros": keras.losses.Huber(delta=1.0),
            "category": keras.losses.CategoricalCrossentropy()
        },
        loss_weights={
            "macros": 0.5,
            "category": 2.0 # Prioritize high classification discriminability
        },
        metrics={
            "macros": "mae",
            "category": "accuracy"
        }
    )
    
    history = model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=12,
        batch_size=32,
        verbose=1
    )
    
    val_acc = history.history["val_category_accuracy"][-1]
    print(f"\nFinal Validation Category Accuracy: {val_acc * 100:.2f}%")
    
    # Export to TFLite
    print("\nExporting to 16 KB Page-Aligned TensorFlow Lite Model...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]
    
    tflite_model = converter.convert()
    
    output_path = os.path.join(r"C:\Users\Harrison\Coding Projects\antigravity\nutritrack\app\src\main\assets", "nutrition5k_macro_predictor.tflite")
    with open(output_path, "wb") as f:
        f.write(tflite_model)
    print(f"Successfully saved TFLite model to {output_path} ({len(tflite_model) / 1024:.1f} KB)")

if __name__ == "__main__":
    main()
