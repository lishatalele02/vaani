# Intent accuracy report (held-out test split)

Dataset: SYNTHETIC hand-written phrases - not real or clinical data.

- Unique phrases: 448  |  held-out test phrases: 112  |  classes: 19
- Accuracy: **0.866**  |  macro precision 0.881  |  macro recall 0.874  |  macro F1 0.867

## By language group

| Group | Accuracy |
|---|---|
| en | 0.833 |
| hi | 0.914 |
| mr | 0.854 |

## Per intent

| Intent | Precision | Recall | F1 | Support |
|---|---|---|---|---|
| express_discomfort | 1.00 | 0.80 | 0.89 | 5 |
| express_fear | 0.67 | 0.80 | 0.73 | 5 |
| express_happiness | 0.62 | 0.83 | 0.71 | 6 |
| express_pain | 1.00 | 1.00 | 1.00 | 6 |
| express_sadness | 0.71 | 0.83 | 0.77 | 6 |
| other_unclear | 1.00 | 0.50 | 0.67 | 8 |
| request_bathroom | 1.00 | 1.00 | 1.00 | 5 |
| request_doctor | 1.00 | 1.00 | 1.00 | 6 |
| request_emergency_help | 0.75 | 0.50 | 0.60 | 6 |
| request_family | 1.00 | 1.00 | 1.00 | 5 |
| request_food | 0.86 | 1.00 | 0.92 | 6 |
| request_go_home | 1.00 | 1.00 | 1.00 | 6 |
| request_go_outside | 1.00 | 1.00 | 1.00 | 6 |
| request_help | 0.71 | 0.83 | 0.77 | 6 |
| request_medicine | 0.86 | 1.00 | 0.92 | 6 |
| request_rest | 0.80 | 0.67 | 0.73 | 6 |
| request_sleep | 0.75 | 1.00 | 0.86 | 6 |
| request_wait | 1.00 | 0.83 | 0.91 | 6 |
| request_water | 1.00 | 1.00 | 1.00 | 6 |

> A curated keyword lexicon (training-only) is added to every fit, so the test split mainly measures generalisation across phrase wording. Scores come from a small synthetic test split and are optimistic; expect lower accuracy on real speech.

Every interpretation is still confirmed by the user before it is spoken, so a wrong guess is a correction, not an action.
