# RUVEN — 90 Major Feature Roadmap

This roadmap is an implementation backlog, not a claim that every item already exists. Ship in small, tested releases; prioritize visual quality, accessibility, privacy, and reliable school workflows over feature count.

## A. Premium visual experience (1–15)
1. Unified premium design system with consistent spacing, typography, radii, and elevation.
2. Adaptive glass surfaces with restrained blur and readable contrast.
3. Refined navy, royal-blue, cyan, and champagne-gold theme presets.
4. Full dark-mode palette with accessible contrast.
5. Light mode with the same visual hierarchy.
6. Per-school brand kit: logo, colors, name, and typography.
7. Animated navigation transitions with reduced-motion support.
8. Skeleton loading states instead of abrupt blank screens.
9. Branded empty states with useful next actions.
10. Consistent success, warning, offline, and error banners.
11. Polished cards with clear hierarchy and responsive layouts.
12. Tablet two-pane layouts where they improve navigation.
13. Dynamic type scaling without clipping.
14. RTL/LTR layout validation and mirrored iconography.
15. Motion and visual-effects controls for lower-powered devices.

## B. Personalization and accessibility (16–25)
16. Per-user theme and accent selection.
17. Saved wallpaper presets and safe image crop/position controls.
18. Home-screen widget-style quick panels inside the app.
19. Customizable dashboard shortcuts.
20. Favorites for frequently used school sections.
21. Arabic-first localization quality pass.
22. Complete English localization pass.
23. Optional bilingual labels.
24. Screen-reader labels and logical focus order.
25. Reduced motion, contrast, and text-size preferences.

## C. Student dashboard and learning (26–40)
26. Role-aware dashboard with relevant content only.
27. Today overview for lessons, homework, and announcements.
28. Weekly timetable with clear current/next lesson highlighting.
29. Homework deadlines and overdue indicators.
30. Subject-based homework filters.
31. Homework completion checklist stored per user.
32. Preparation and lesson-material library.
33. Attachments preview and safe download handling.
34. Search across published school content.
35. Saved posts and bookmarks.
36. Recently viewed content.
37. Calendar view for school events and deadlines.
38. Exam schedule and reminder settings.
39. Personal study planner.
40. Study progress summary that avoids public student ranking.

## D. Communication and notifications (41–50)
41. Notification center with unread counts.
42. Notification categories and per-category preferences.
43. Announcement pinning and expiration dates.
44. Read/unread state synchronization across devices.
45. Push notifications through a properly configured provider.
46. Quiet hours and notification batching.
47. Announcement attachments with file-size/type validation.
48. Acknowledgement tracking for critical notices.
49. Clear offline and retry states for failed refreshes.
50. In-app feedback and issue reporting with consent.

## E. Teacher and school operations (51–65)
51. Teacher workspace for assigned subjects and sections.
52. Draft, preview, schedule, and publish workflow for school posts.
53. Reusable homework templates.
54. Reusable lesson-preparation templates.
55. Timetable editing with conflict validation.
56. Attendance workflow with an auditable change history.
57. Leave requests with status tracking.
58. Administrative decisions with controlled authorization.
59. Seal/signature registry with ownership checks.
60. Daily reports with publish/unpublish controls.
61. Staff directory with role-appropriate visibility.
62. Bulk roster import with validation and clear error reporting.
63. Duplicate detection that distinguishes actual duplicates from network errors.
64. Export of authorized school reports.
65. Administrative activity log for sensitive operations.

## F. Multi-school commercial platform (66–76)
66. School tenant model with immutable school identifiers.
67. Strict row-level data isolation between schools.
68. School onboarding and verification workflow.
69. Per-school logo, theme, academic year, and grade structure.
70. Per-school role and permission configuration within safe limits.
71. Subscription plans and entitlement checks.
72. Trial mode with explicit expiry and conversion flow.
73. School-level usage and service-health dashboard.
74. Tenant-aware support and diagnostics without exposing student data.
75. Data export and school offboarding workflow.
76. Versioned configuration and migration strategy for tenant upgrades.

## G. Security, reliability, and privacy (77–86)
77. Server-side authorization for every privileged operation.
78. Automated tests for row-level security and cross-tenant isolation.
79. Least-privilege database grants.
80. Rate limits and abuse protection for account and publishing flows.
81. Secure session expiry and sign-out behavior.
82. No service-role keys or privileged secrets in the mobile client.
83. Encrypted transport and safe attachment handling.
84. Backup and restore drills with documented recovery objectives.
85. Privacy notice, retention schedule, and data-deletion process.
86. Dependency, build, and release integrity checks.

## H. Product quality and operations (87–90)
87. Automated UI smoke tests for sign-in and core student workflows.
88. Crash/error diagnostics with privacy-conscious data minimization.
89. Release notes, staged rollout, and rollback checklist.
90. School pilot evaluation dashboard covering reliability, adoption, and support load.

## Delivery gates
- Implement features in small batches and review diffs before merging.
- Never claim a feature is complete until the code, tests, and permissions have been checked.
- Any change affecting student data must include server-side authorization and privacy review.
- Do not enable multi-school sales until tenant isolation has been tested with separate school accounts.
