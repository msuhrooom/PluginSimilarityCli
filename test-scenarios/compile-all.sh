#!/bin/bash

# Script to compile all test scenario plugins
set -e

BASE_DIR="/Users/not_danbin/Desktop/me/IC Computing Year 2/intern/PluginSimilarity/test-scenarios"
cd "$BASE_DIR"

echo "Creating output directories..."
mkdir -p jars
mkdir -p build

echo ""
echo "=== Scenario 1: Same Behavior, Different Names ==="
echo "Compiling UserDataManager..."
javac -d build UserDataManager.java
cd build && jar cf ../jars/scenario1-userdata.jar com/example/usermanager/*.class && cd ..
rm -rf build/com

echo "Compiling PersonInfoHandler..."
javac -d build PersonInfoHandler.java
cd build && jar cf ../jars/scenario1-personinfo.jar com/acme/personhandler/*.class && cd ..
rm -rf build/com

echo ""
echo "=== Scenario 2: Same Structure, Different Behavior ==="
echo "Compiling MathCalculator..."
javac -d build MathCalculator.java
cd build && jar cf ../jars/scenario2-math.jar com/example/calculator/*.class && cd ..
rm -rf build/com

echo "Compiling StringCalculator..."
javac -d build StringCalculator.java
cd build && jar cf ../jars/scenario2-string.jar com/example/calculator/*.class && cd ..
rm -rf build/com

echo ""
echo "=== Scenario 3: Version Churn ==="
echo "Compiling ShoppingCartV1..."
javac -d build ShoppingCartV1.java
cd build && jar cf ../jars/scenario3-cart-v1.jar com/shop/cart/*.class && cd ..
rm -rf build/com

echo "Compiling ShoppingCartV2..."
javac -d build ShoppingCartV2.java
cd build && jar cf ../jars/scenario3-cart-v2.jar com/shop/cart/*.class && cd ..
rm -rf build/com

echo ""
echo "=== Scenario 4: Completely Different ==="
echo "Compiling ImageProcessor..."
javac -d build ImageProcessor.java
cd build && jar cf ../jars/scenario4-image.jar com/graphics/imaging/*.class && cd ..
rm -rf build/com

echo "Compiling NetworkManager..."
javac -d build NetworkManager.java
cd build && jar cf ../jars/scenario4-network.jar com/network/connection/*.class && cd ..
rm -rf build/com

echo ""
echo "=== Scenario 5: Partial Overlap ==="
echo "Compiling FileUtility..."
javac -d build FileUtility.java
cd build && jar cf ../jars/scenario5-file.jar com/utils/files/*.class && cd ..
rm -rf build/com

echo "Compiling DirectoryManager..."
javac -d build DirectoryManager.java
cd build && jar cf ../jars/scenario5-directory.jar com/utils/filesystem/*.class && cd ..
rm -rf build/com

echo ""
echo "✓ All plugins compiled successfully!"
echo "JAR files are in: $BASE_DIR/jars/"
ls -lh jars/*.jar
