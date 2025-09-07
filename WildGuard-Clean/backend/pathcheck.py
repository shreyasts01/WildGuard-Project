import yaml

yaml_path = "final_dataset/data.yaml"

with open(yaml_path, "r") as f:
    content = f.read()
    print("Raw YAML content:\n", content)  # Debug: See what's actually in the file

try:
    data = yaml.safe_load(content)
    print("\nParsed YAML:\n", data)  # Debug: See how YAML is interpreted
except yaml.YAMLError as e:
    print("\nYAML Error:", e)  # Will show syntax issues