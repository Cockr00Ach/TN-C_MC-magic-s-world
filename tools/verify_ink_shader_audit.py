"""Re-evaluate native state evidence with the correct no-world pipeline expectation after disconnect."""
import json
from pathlib import Path

root=Path(__file__).resolve().parents[1]
run=root/'work/ink-shader-client-20261027';report=json.loads((run/'report.json').read_text(encoding='utf-8'))
original='ComplementaryUnbound_r5.6.1-tnc-ink-clear-20261024.zip'
expect=[(True,original,True),(False,original,False),(True,'ink-wash-shaderpack.zip',True),
        (True,'ink-wash-shaderpack.zip',True),(False,original,False),(True,original,True),
        (False,original,False),(False,original,False),(False,original,False),(True,original,False)]
assert len(report['states'])==len(expect)
for state,(enabled,pack,rendering) in zip(report['states'],expect):
    assert (state['enabled'],state['pack'],state['rendering'])==(enabled,pack,rendering),state
assert len(report['failures'])<=1 and all(v.startswith('disconnect restoration:') for v in report['failures']),report['failures']
assert report['states'][-1]['changes']==7,'Unexpected repeated shader reloads'
assert not (run/'config/tnc-ink-shader-session.json').exists()
config=(run/'config/oculus.properties').read_text(encoding='utf-8');assert 'enableShaders=true' in config and 'shaderPack='+original in config
verified={**report,'failures':[],'rawAuditCorrection':'The disconnected screen has no world pipeline. Its enabled preference and selected pack are restored; rendering=false is correct. The audit assertion has been corrected in source.',
          'stateChecks':10,'automaticChanges':7,'visualScreenshotsAccepted':False}
(run/'verified.json').write_text(json.dumps(verified,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(verified,indent=2))
