# QNU timetable fixtures

These files were extracted from two real QNU offline timetable pages and sanitized before entering Git.

- `qnu-timetable-standard.gbk.html`: regular timetable structure with a small change-detail section.
- `qnu-timetable-changes.gbk.html`: denser timetable with multiple records per cell and continuation rows in the change-detail table.

Both HTML files are encoded as GBK and preserve the original DOM shape. They contain synthetic student, class, course, teacher, room, and course-number values. Do not convert them to UTF-8 when editing. The original MHT files are intentionally not committed because they contain personal timetable identifiers.

See `docs/research/泉师课表MHT结构分析_V1.0.md` for the observed structure and parsing considerations.
