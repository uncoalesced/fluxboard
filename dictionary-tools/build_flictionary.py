import os
import math
import struct
import sys

def build_trie(words_file):
    class Node:
        def __init__(self):
            self.children = {}
            self.freq = 0
            self.is_terminal = False
            self.offset = -1
            self.char = ''

    root = Node()
    max_freq = 0

    print("Parsing words...")
    with open(words_file, 'r', encoding='utf-8') as f:
        for line in f:
            parts = line.strip().split()
            if len(parts) >= 2:
                word = parts[0]
                try:
                    freq = int(parts[1])
                except ValueError:
                    continue
                if freq > max_freq:
                    max_freq = freq

    print(f"Max frequency found: {max_freq}")

    with open(words_file, 'r', encoding='utf-8') as f:
        for line in f:
            parts = line.strip().split()
            if len(parts) >= 2:
                word = parts[0]
                try:
                    freq = int(parts[1])
                except ValueError:
                    continue
                
                # Normalize frequency 1-255 logarithmically
                norm_freq = max(1, min(255, int((math.log(freq + 1) / math.log(max_freq + 1)) * 255)))
                
                # Insert into trie
                current = root
                for char in word:
                    if char not in current.children:
                        current.children[char] = Node()
                        current.children[char].char = char
                    current = current.children[char]
                
                current.is_terminal = True
                if norm_freq > current.freq:
                    current.freq = norm_freq

    print("Trie built. Calculating offsets...")

    # Flatten the tree using BFS to optimize locality
    nodes = []
    queue = [root]
    while queue:
        node = queue.pop(0)
        nodes.append(node)
        for char, child in sorted(node.children.items()):
            queue.append(child)

    # Calculate offsets
    current_offset = 4 # 4 bytes for magic header 'FLCT'
    for node in nodes:
        node.offset = current_offset
        child_count = min(255, len(node.children)) # clamp to 255
        node_size = 3 + (child_count * 6)
        current_offset += node_size

    print(f"Total nodes: {len(nodes)}")
    print(f"Expected file size: {current_offset} bytes ({current_offset / 1024 / 1024:.2f} MB)")

    return nodes

def write_flictionary(nodes, output_file):
    print("Writing binary dictionary...")
    with open(output_file, 'wb') as f:
        # Magic header
        f.write(b'FLCT')
        
        for node in nodes:
            children_items = sorted(node.children.items())[:255] # max 255 children
            child_count = len(children_items)
            
            # Node header: freq (1B), is_terminal (1B), child_count (1B)
            f.write(struct.pack('>BBB', node.freq, 1 if node.is_terminal else 0, child_count))
            
            # Children: char (2B utf-16be), child_offset (4B int)
            for char, child in children_items:
                # Get UTF-16 encoded char, take first 2 bytes
                char_bytes = char.encode('utf-16-be')
                if len(char_bytes) > 2:
                    char_bytes = char_bytes[:2]
                elif len(char_bytes) < 2:
                    char_bytes = char_bytes.ljust(2, b'\x00')
                
                f.write(char_bytes)
                f.write(struct.pack('>I', child.offset))

    print(f"Successfully wrote {output_file}")

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python build_flictionary.py <input_corpus.txt> <output.bin>")
        sys.exit(1)
    
    in_file = sys.argv[1]
    out_file = sys.argv[2]
    
    nodes = build_trie(in_file)
    write_flictionary(nodes, out_file)
