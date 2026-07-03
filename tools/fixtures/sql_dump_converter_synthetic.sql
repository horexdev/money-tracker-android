--
-- Synthetic PostgreSQL COPY fixture for tools/sql_dump_to_mtbackup.py.
-- Values are intentionally artificial and contain no real user data.
--

COPY public.users (id, username, first_name, last_name, created_at, updated_at, display_currencies, language, notify_budget_alerts, notify_recurring_reminders, notify_weekly_summary, notify_goal_milestones, stats_chart_style, animate_numbers, theme, hide_amounts) FROM stdin;
1001	demo_user	Demo	Person	2026-07-01 08:00:00+00	2026-07-02 09:30:00+00	usd, eur,USD	ru	t	t	f	t	stacked_bar	t	dark	t
2002	other_user	Other	Person	2026-07-01 08:00:00+00	2026-07-02 09:30:00+00	usd	en	t	f	f	f	donut	\N	system	f
\.
COPY public.accounts (id, user_id, name, icon, color, type, currency_code, is_default, include_in_total, created_at, updated_at) FROM stdin;
11	1001	Main wallet	wallet	#2563eb	checking	usd	t	t	2026-07-01 08:05:00+00	2026-07-01 08:05:00+00
12	1001	Reserve	piggy-bank	#0f766e	savings	eur	f	t	2026-07-01 08:10:00+00	2026-07-01 08:10:00+00
13	2002	Other wallet	wallet	#64748b	checking	usd	t	t	2026-07-01 08:05:00+00	2026-07-01 08:05:00+00
\.
COPY public.categories (id, user_id, name, icon, type, updated_at, deleted_at, color, is_protected) FROM stdin;
21	1001	Dining	fork-knife	expense	2026-07-01 08:15:00+00	\N	#ef4444	f
22	1001	Salary	briefcase	income	2026-07-01 08:16:00+00	\N	#10b981	f
23	\N	Transfer	arrows-left-right	transfer	2026-07-01 08:17:00+00	\N	#6366f1	t
24	2002	Other	tags	expense	2026-07-01 08:18:00+00	\N	#64748b	f
\.
COPY public.transactions (id, user_id, type, amount_cents, category_id, note, created_at, currency_code, account_id, is_adjustment, snapshot_date) FROM stdin;
31	1001	expense	2750	21	Lunch	2026-07-01 12:00:00+00	usd	11	f	2026-07-01
32	1001	income	250000	22	Paycheck	2026-07-01 13:00:00+00	usd	11	f	2026-07-01
33	1001	expense	50000	23	Move to reserve	2026-07-02 10:00:00+00	usd	11	f	2026-07-02
34	1001	income	46000	23	Reserve received	2026-07-02 10:00:01+00	eur	12	f	2026-07-02
35	2002	expense	9999	24	Other	2026-07-01 12:00:00+00	usd	13	f	2026-07-01
\.
COPY public.transfers (id, user_id, from_account_id, to_account_id, amount_cents, from_currency_code, to_currency_code, exchange_rate, note, created_at, from_tx_id, to_tx_id) FROM stdin;
41	1001	11	12	50000	usd	eur	0.92	Move to reserve	2026-07-02 10:00:00+00	33	34
\.
COPY public.budgets (id, user_id, category_id, limit_cents, period, currency_code, notify_at_percent, created_at, updated_at, last_notified_at, notifications_enabled, last_notified_percent) FROM stdin;
51	1001	21	75000	monthly	usd	80	2026-07-01 09:00:00+00	2026-07-02 09:00:00+00	2026-07-02 09:00:00+00	t	50
\.
COPY public.recurring_transactions (id, user_id, category_id, type, amount_cents, currency_code, note, frequency, next_run_at, is_active, created_at, updated_at, account_id) FROM stdin;
61	1001	21	expense	1500	usd	Membership	weekly	2026-07-08 09:00:00+00	t	2026-07-01 09:15:00+00	2026-07-02 09:15:00+00	11
\.
COPY public.savings_goals (id, user_id, name, target_cents, current_cents, currency_code, deadline, created_at, updated_at, account_id) FROM stdin;
71	1001	Trip fund	300000	46000	eur	2026-12-31	2026-07-01 09:20:00+00	2026-07-02 09:20:00+00	12
\.
COPY public.goal_transactions (id, goal_id, user_id, type, amount_cents, created_at) FROM stdin;
81	71	1001	deposit	46000	2026-07-02 10:00:01+00
\.
COPY public.transaction_templates (id, user_id, name, type, amount_cents, amount_fixed, category_id, account_id, currency_code, note, sort_order, created_at, updated_at) FROM stdin;
91	1001	Lunch preset	expense	2750	t	21	11	usd	Lunch	1	2026-07-01 09:30:00+00	2026-07-02 09:30:00+00
\.
COPY public.exchange_rate_snapshots (id, snapshot_date, base_currency, target_currency, rate, created_at) FROM stdin;
101	2026-07-01	usd	eur	0.92	2026-07-01 00:00:00+00
102	2026-07-01	usd	rub	80.0	2026-07-01 00:00:00+00
\.
