EVENTS = '''
SELECT * FROM cinema.raw_events FINAL
WHERE ingested_at <= {watermark:DateTime64(6)}
'''

DAILY = '''
WITH events AS (''' + EVENTS + ''')
SELECT count() AS source_events,
       uniqExact(user_id) AS dau,
       countIf(event_type='VIEW_STARTED') AS started,
       countIf(event_type='VIEW_FINISHED') AS finished,
       sumIf(progress_seconds, event_type='VIEW_FINISHED') /
           nullIf(countIf(event_type='VIEW_FINISHED'), 0) AS average_watch_seconds,
       countIf(event_type='VIEW_FINISHED') /
           nullIf(countIf(event_type='VIEW_STARTED'), 0) AS conversion
FROM events WHERE toDate(timestamp) = {day:Date}
'''

MOVIES = '''
WITH events AS (''' + EVENTS + ''')
SELECT movie_id, count() AS views
FROM events WHERE toDate(timestamp)={day:Date} AND event_type='VIEW_STARTED'
GROUP BY movie_id ORDER BY views DESC, movie_id
'''

DEVICES = '''
WITH events AS (''' + EVENTS + ''')
SELECT toString(device_type) AS device_type, count() AS events
FROM events WHERE toDate(timestamp)={day:Date}
GROUP BY device_type
'''

RETENTION = '''
WITH events AS (''' + EVENTS + '''),
first_views AS
(
    SELECT user_id, min(toDate(timestamp)) AS cohort_date
    FROM events WHERE event_type='VIEW_STARTED'
    GROUP BY user_id
),
activity AS
(
    SELECT user_id, groupUniqArray(toDate(timestamp)) AS active_days
    FROM events GROUP BY user_id
)
SELECT day_number, count() AS cohort_size,
       countIf(has(active_days, addDays(cohort_date, day_number))) AS returned_users
FROM first_views INNER JOIN activity USING (user_id)
ARRAY JOIN range(8) AS day_number
WHERE cohort_date={day:Date}
GROUP BY day_number ORDER BY day_number
'''
