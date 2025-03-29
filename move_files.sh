#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

PUMPKIN_ROOT="$1"
SCRIPT_ROOT="$(dirname -- "$(readlink -f -- "$0")")"
OUTPUT_DIRECTORY="$SCRIPT_ROOT/run/pumpkin_extractor_output"

if ! [ -d "$PUMPKIN_ROOT" ]; then
  echo "The input must be a valid directory!"
  exit 1
fi

if ! [ -d "$OUTPUT_DIRECTORY" ]; then
  echo "Invalid file location directory!"
  exit 1
fi

# Base asset file
OUTPUT_SUBDIR="$PUMPKIN_ROOT/assets"

if ! [ -d "$OUTPUT_SUBDIR" ]; then
  echo "Invalid output location directory!"
  exit 1
fi

declare -a FILES=(
  "items.json"
  "noise_parameters.json"
  "message_type.json"
  "en_us.json"
  "entity_statuses.json"
  "sound_category.json"
  "fluids.json"
  "tags.json"
  "entity_pose.json"
  "carver.json"
  "game_rules.json"
  "game_event.json"
  "sounds.json"
  "status_effects.json"
  "entities.json"
  "scoreboard_display_slot.json"
  "attributes.json"
  "placed_feature.json"
  "synced_registries.json"
  "spawn_egg.json"
  "particles.json"
  "multi_noise_biome_tree.json"
  "properties.json"
  "damage_type.json"
  "packets.json"
  "screens.json"
  "gen_features.json"
  "biome.json"
  "blocks.json"
  "recipes.json"
  "chunk_status.json"
  "chunk_gen_settings.json"
  "density_function.json"
  "world_event.json"
)

for FILE in "${FILES[@]}"; do
  cp "$OUTPUT_DIRECTORY/$FILE" "$OUTPUT_SUBDIR/$FILE"
done

# World test files
OUTPUT_SUBDIR="$PUMPKIN_ROOT/pumpkin-world/assets"

if ! [ -d "$OUTPUT_SUBDIR" ]; then
  echo "Invalid output location directory!"
  exit 1
fi

declare -a FILES=(
  "no_blend_no_beard_only_cell_cache_once_cache_0_0.chunk"
  "no_blend_no_beard_only_cell_cache_0_0.chunk"
  "biome_no_blend_no_beard_0.json"
  "multi_noise_sample_no_blend_no_beard_0_0_0.json"
  "no_blend_no_beard_only_cell_cache_interpolated_0_0.chunk"
  "no_blend_no_beard_-595_544.chunk"
  "no_blend_no_beard_0_0.chunk"
  "no_blend_no_beard_surface_badlands_-595_544.chunk"
  "no_blend_no_beard_-119_183.chunk"
  "no_blend_no_beard_7_4.chunk"
  "no_blend_no_beard_surface_frozen_ocean_-119_183.chunk"
  "no_blend_no_beard_surface_0_0.chunk"
  "multi_noise_biome_source_test.json"
  "no_blend_no_beard_only_cell_cache_flat_cache_0_0.chunk"
  "density_function_tests.json"
  "no_blend_no_beard_13579_-6_11.chunk"
  "no_blend_no_beard_surface_13579_-6_11.chunk"
  "no_blend_no_beard_13579_-2_15.chunk"
  "no_blend_no_beard_surface_13579_-2_15.chunk"
  "no_blend_no_beard_surface_13579_-7_9.chunk"
  "biome_mixer.json"
)

for FILE in "${FILES[@]}"; do
  cp "$OUTPUT_DIRECTORY/$FILE" "$OUTPUT_SUBDIR/$FILE"
done

echo "Files moved."
