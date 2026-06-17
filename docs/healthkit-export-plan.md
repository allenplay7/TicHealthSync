# HealthKit Export Plan

Do not implement HealthKit export until the iOS app can receive and locally store fake watch records.

Planned mapping:

| TicHealthSync type | HealthKit type |
| --- | --- |
| `heart_rate` | `HKQuantityTypeIdentifier.heartRate` |
| `step_count` | `HKQuantityTypeIdentifier.stepCount` |
| `active_energy` | `HKQuantityTypeIdentifier.activeEnergyBurned` |
| `distance` | `HKQuantityTypeIdentifier.distanceWalkingRunning` |
| `workout_start` / `workout_end` | `HKWorkout` later |
| `sleep_segment` | `HKCategoryTypeIdentifier.sleepAnalysis` later |
| `oxygen_saturation` | `HKQuantityTypeIdentifier.oxygenSaturation` later, only if reliable data exists |

