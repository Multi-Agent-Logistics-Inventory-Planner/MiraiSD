# Phase Cost-Impact Record

Copy this section into each phase delivery record before implementation approval.

## Decision metadata

- Phase:
- Owner:
- Date:
- Decision: Approve / revise / defer
- Cost ceiling after change: USD 300 per month

## Recurring cost

| Item/provider | Current monthly | Projected monthly | Delta | Pricing evidence/date | Usage assumption |
| --- | ---: | ---: | ---: | --- | --- |
| | | | | | |

Include taxes, storage, network/egress, backups, image retention, CI minutes, seats and paid support.
Do not use a free-tier price without documenting its limit and the expected usage.

## Temporary migration cost

| Item | Duration | Monthly-equivalent cost | Maximum one-time total | Shutdown criterion |
| --- | ---: | ---: | ---: | --- |
| | | | | |

Provider overlap MUST have a named shutdown owner and date/criterion.

## Engineering and operational cost

- Implementation estimate:
- Ongoing maintenance owner and expected hours/month:
- New on-call/runbook burden:
- Backup/restore or compliance impact:
- Exit/reversal cost:

## Capacity evidence

- Current measured utilization:
- Projected utilization and headroom:
- Most important pricing/capacity uncertainty:
- Trigger for upgrading or adding the deferred capability:

## Approval gate

- [ ] The resulting recurring-stack projection is below USD 300/month.
- [ ] Every new paid service has a free/existing alternative comparison.
- [ ] Temporary overlap has a maximum duration and shutdown owner.
- [ ] The phase can be rolled back without retaining an unplanned paid dependency.
- [ ] Actual cost will be recorded after the first full billing period.
