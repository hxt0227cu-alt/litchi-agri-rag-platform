## Dataset Layout

This directory stores source materials and training assets. The application does not
auto-load files from here yet, so use this as a workspace for collecting and cleaning
data before importing it into the running system.

### `knowledge/`

- `raw/`: original documents collected from websites, manuals, or reports
- `cleaned/`: cleaned `txt` or `md` files ready for upload into the app
- `metadata.csv`: document source metadata
- PDF cleaning command:
  `python scripts/clean_knowledge_docs.py --include-pdf`
- If PDF extraction quality is poor, check `cleaned/cleaning-report.csv` and handle files
  marked `needs_ocr` separately.

### `graph/`

- `entities/`: entity tables for knowledge graph import
- `relations/`: relationship tables for knowledge graph import

### `images/`

- `raw/`: original orchard photos
- `labeled/`: YOLO training set with `images/` and `labels/`
- `metadata.csv`: image source and labeling metadata
- `data.yaml`: YOLO dataset config template

### `models/`

Place the trained YOLO weight file at `models/yolov8-litchi.pt`.
