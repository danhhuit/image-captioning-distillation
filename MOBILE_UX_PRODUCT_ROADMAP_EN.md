# Mobile Image Captioning mini-app roadmap

[Bản tiếng Việt](MOBILE_UX_PRODUCT_ROADMAP.md) · [Back to README](README_EN.md)

This document records product status for version 1.7.0 and the work that must be verified before wider distribution. It does not replace the issue tracker or test evidence.

## 1. Available now

- Five-tab navigation: History, Favourites, Capture, Models, and Profile.
- Main and experimental model groups.
- Teacher–Student, dataset, KD method, and CIDEr-D details.
- On-device ONNX inference.
- English–Vietnamese translation, Text-to-Speech, and image/caption sharing.
- Local history and favourites.
- Vietnamese/English and light/dark interfaces.
- Registration, sign-in, Google Sign-In, and password reset through the API.
- Permission-based account administration.

## 2. Displayed model catalogue

Main models:

1. MS COCO 2014 — ExpansionNet v2 → MobileNetV3-Large — Sequence KD.
2. MS COCO 2014 — ExpansionNet v2 → MobileNetV3-Large — Dense-Mapped Logit-KD.
3. MS COCO 2014 — ExpansionNet v2 → MobileNetV3-Large — Hybrid KD.
4. MS COCO 2014 — BLIP Base → MobileViT-S — Sequence KD.
5. MS COCO 2014 — SigLIP ViT-B/16 → TinyViT-5M — Direct Logit-KD.

Experimental models:

1. V1 FP32.
2. MS COCO 2014 — CLIP ViT-B/32 → MobileNetV3-Large — Direct Logit-KD.
3. Flickr30K — ExpansionNet v2 → MobileNetV3-Large — Sequence KD.
4. Flickr30K — ExpansionNet v2 → MobileNetV3-Large — Dense-Mapped Logit-KD.
5. Flickr30K — ExpansionNet v2 → MobileNetV3-Large — Hybrid KD.
6. Flickr30K — BLIP Base → MobileViT-S — Sequence KD.
7. Flickr30K — SigLIP ViT-B/16 → TinyViT-5M — Direct Logit-KD.

## 3. Work required before release

### P0

- Test sign-in on Emulator, USB, and Wi-Fi using the new API guide.
- Verify that reset codes reach the correct email.
- Test account locking, unlocking, editing, and deletion.
- Confirm every model registry entry resolves to a real asset.
- Test cold start, rotation, and image/camera permission changes.
- Test small screens, large fonts, and long strings in both languages.

### P1

- Run a shared physical-device benchmark for S1, S2, and S3.
- Record p50/p95, PSS, package size, and thermal state with one protocol.
- Test image/caption sharing with several target apps.
- Test repeated switching between checkpoints.
- Add accessibility tests for labels and touch targets.

### P2

- Improve model comparison.
- Add user-triggered history export.
- Finalise privacy and retention rules.
- Evaluate separate model-package delivery if APK size becomes a constraint.

## 4. Definition of done

A roadmap item is complete only when it has:

1. merged source code;
2. automated tests or a repeatable manual test record;
3. output screenshots or logs;
4. updated user documentation;
5. no related open P0 defect.

## 5. Reporting rules

- Distinguish main and experimental models.
- Do not call a branch an improvement when CIDEr-D is below baseline.
- Do not report Emulator measurements as physical-device results.
- Do not claim background synchronisation or another untested feature.
- State dataset, seed, checkpoint, and quantisation type with every result.
