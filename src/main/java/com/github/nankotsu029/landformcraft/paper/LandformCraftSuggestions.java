package com.github.nankotsu029.landformcraft.paper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Pure command-tree completion logic, separated from Bukkit data access. */
final class LandformCraftSuggestions {
    private LandformCraftSuggestions() {
    }

    static List<String> complete(
            String[] args,
            boolean canSelect,
            boolean canApply,
            boolean canUndo,
            Collection<String> placementIds,
            Collection<String> releases,
            Collection<String> worlds,
            List<String> coordinates
    ) {
        if (args.length == 0) {
            return List.of();
        }
        List<String> candidates = new ArrayList<>();
        if (args.length == 1) {
            candidates.add("help");
            if (canSelect) {
                candidates.add("selection");
            }
            if (canApply || canUndo) {
                candidates.add("apply");
            }
            if (canUndo) {
                candidates.add("undo");
            }
            return matching(candidates, args[0]);
        }

        if (args[0].equalsIgnoreCase("apply")) {
            if (args.length == 2) {
                if (canApply) {
                    candidates.addAll(List.of("plan", "execute", "status"));
                }
                if (canUndo) {
                    candidates.add("undo");
                }
            } else if (args.length == 3) {
                if (args[1].equalsIgnoreCase("plan") && canApply) {
                    candidates.addAll(releases);
                } else if (((args[1].equalsIgnoreCase("execute") || args[1].equalsIgnoreCase("status"))
                        && canApply) || (args[1].equalsIgnoreCase("undo") && canUndo)) {
                    candidates.addAll(placementIds);
                }
            } else if (args.length == 4 && args[1].equalsIgnoreCase("plan") && canApply) {
                candidates.addAll(worlds);
            } else if (args.length >= 5 && args.length <= 7
                    && args[1].equalsIgnoreCase("plan") && canApply) {
                candidates.add(coordinates.get(args.length - 5));
            }
        } else if (args[0].equalsIgnoreCase("undo") && canUndo) {
            if (args.length == 2) {
                candidates.add("execute");
            } else if (args.length == 3 && args[1].equalsIgnoreCase("execute")) {
                candidates.addAll(placementIds);
            }
        }
        return matching(candidates, args[args.length - 1]);
    }

    static List<String> completeRelease2(
            String[] args,
            boolean canPlan,
            boolean canConfirm,
            boolean canExecute,
            boolean canStatus,
            boolean canUndo,
            boolean canRecovery,
            Collection<String> placementIds,
            Collection<String> releases,
            Collection<String> allowedWorlds,
            List<String> coordinates
    ) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("r2")) {
            return List.of();
        }
        List<String> candidates = new ArrayList<>();
        if (args.length == 2) {
            if (canPlan) candidates.add("plan");
            if (canConfirm) candidates.add("confirm");
            if (canExecute) candidates.add("execute");
            if (canStatus) candidates.add("status");
            if (canUndo) candidates.addAll(List.of("undo-plan", "undo-execute"));
            if (canRecovery) candidates.addAll(List.of(
                    "recover-diagnose", "recover-plan", "recover-execute"));
        } else if (args.length == 3) {
            String operation = args[1].toLowerCase(Locale.ROOT);
            if (operation.equals("plan") && canPlan) {
                candidates.addAll(releases);
            } else if ((operation.equals("confirm") && canConfirm)
                    || (operation.equals("execute") && canExecute)
                    || (operation.equals("status") && canStatus)
                    || ((operation.equals("undo-plan") || operation.equals("undo-execute")) && canUndo)
                    || (operation.equals("recover-diagnose") && canRecovery)) {
                candidates.addAll(placementIds);
            } else if ((operation.equals("recover-plan") || operation.equals("recover-execute"))
                    && canRecovery) {
                candidates.addAll(List.of("accept", "rollback"));
            }
        } else if (args.length == 4) {
            String operation = args[1].toLowerCase(Locale.ROOT);
            if (operation.equals("plan") && canPlan) {
                candidates.addAll(allowedWorlds);
            } else if ((operation.equals("recover-plan") || operation.equals("recover-execute"))
                    && canRecovery) {
                candidates.addAll(placementIds);
            }
        } else if (args.length >= 5 && args.length <= 7
                && args[1].equalsIgnoreCase("plan") && canPlan) {
            candidates.add(coordinates.get(args.length - 5));
        }
        // Confirmation tokens are intentionally never suggested.
        return matching(candidates, args[args.length - 1]);
    }

    private static List<String> matching(Collection<String> candidates, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(normalized))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }
}
