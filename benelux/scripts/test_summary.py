from pathlib import Path
import xml.etree.ElementTree as ET
reports = list(Path('app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
if not reports:
    raise SystemExit('Geen testresultaten gevonden')
tests = failures = errors = skipped = 0
for report in sorted(reports):
    suite = ET.parse(report).getroot()
    values = [int(suite.get(k, 0)) for k in ('tests', 'failures', 'errors', 'skipped')]
    tests += values[0]
    failures += values[1]
    errors += values[2]
    skipped += values[3]
    print(f'{suite.get("name")}: tests={values[0]} failures={values[1]} errors={values[2]} skipped={values[3]}')
print(f'RECEIPT_TEST_TOTAL tests={tests} failures={failures} errors={errors} skipped={skipped}')
if tests < 35 or failures or errors or skipped:
    raise SystemExit('Niet alle verplichte tests zijn geslaagd')
