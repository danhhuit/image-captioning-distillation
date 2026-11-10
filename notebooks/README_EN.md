# Research notebook catalogue

[Bản tiếng Việt](README.md) · [Back to README](../README_EN.md)

This directory contains selected notebooks for reproducing data preparation, training, knowledge distillation, validation, and deployment packaging. Notebooks are grouped by research branch; verify the original output and manifests before using reported values.

## 1. Layout

```text
notebooks/
├── mscoco/       # MS COCO 2014
├── flickr30k/    # Flickr30K
└── mobile/       # ONNX, INT8, Android locks, and benchmarks
```

Each notebook should state:

- a short task-specific name;
- required inputs;
- locked seed and configuration;
- Train/Validation/Test boundaries;
- outputs, manifests, and checksums when applicable;
- completion state or stopping reason.

## 2. Run a notebook

1. Open it on Kaggle.
2. Attach exactly the outputs/datasets listed under **Required inputs**.
3. Select the documented Accelerator and Internet setting.
4. use **Save Version**, not only an interactive session;
5. download the complete output and log;
6. verify manifests, checkpoints, seeds, and Test status before continuing.

Avoid unnecessary inputs. Duplicate files can make a name/hash lookup return multiple matches and fail a gate.

## 3. Scientific rules

- Train is used for parameter updates.
- Validation selects checkpoints, drives early stopping, and locks the method.
- Test is opened once after the method is locked.
- Do not tune hyperparameters using Test results.
- Every aggregate result must trace back to a seed and checkpoint.
- Compare KD with a baseline using a corresponding architecture and budget.
- Clearly label ×100 display values; machine-readable files should keep native values.

## 4. Naming

Use:

```text
<dataset>-<teacher-student>-<method>-<stage>.ipynb
```

Examples:

```text
mscoco-t1s1-hybrid-aggregate.ipynb
f30k-t3s3-int8-validation.ipynb
```

Do not include usernames, personal paths, or Kaggle session identifiers.

## 5. Reproduce outside Kaggle

Notebooks may assume `/kaggle/input` and `/kaggle/working`. For another environment, set variables or edit the first configuration cell rather than scattering path changes across cells.

Before publication:

- export the executed notebook;
- retain logs;
- record package versions;
- retain input/output checksums;
- confirm no secret or account data is included.

## 6. Scope

This is a reproducibility package, not the full experiment history. Do not manually edit generated outputs because that would break their audit trail.
