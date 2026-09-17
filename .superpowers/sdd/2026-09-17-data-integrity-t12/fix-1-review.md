# Round1 scoped re-review

ADDRESSED: source replacement (IntegrityView.vue:68,114), applied date warning (IntegrityIssuesTable.vue:52), QUEUED note (IntegritySummary.vue:27).
NOT ADDRESSED: rejected pending + invalid fromCheckId. IntegrityView.vue:139 validates before copyStarted and selection/confirmation clearing, so local invalid ID returns an error without blocking old scope. Move transition before validation; add rejected-pending invalid-ID regression. Important, original lifecycle finding remains open. No other Critical/Important fix regression found.
