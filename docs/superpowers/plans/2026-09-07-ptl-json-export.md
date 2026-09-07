# PTL Website JSON Portfolio Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a lossless JSON portfolio export to the Pair Trading Lab website so users can migrate their portfolios into PTL Trader before the service shuts down.

**Architecture:** A new `cmd_export_json()` action on `PortManagerController`, beside the existing `cmd_export()` CSV action. The document-shaping logic is extracted into a pure static method so it can be verified without a database, and the controller method is thin I/O around it. The emitted document matches the shape of `GET /portfolios` from the REST API, which is exactly what PTL Trader's `PortfolioList.updateFromJson()` already parses.

**Tech Stack:** PHP 8.3, Smarty templates, Knockout.js, MariaDB.

**Spec:** `docs/superpowers/specs/2026-09-07-ptl-decoupling-local-storage-design.md` (§10), in the `ptltrader` repository.

> **This plan targets a different repository: `../pairtradinglab`.** All paths below are relative to that repository root. It is kept alongside the spec because it implements one section of it.

## Global Constraints

- **This work ships and is announced BEFORE the PTL service closes.** It is the only migration path; without it users cannot move their portfolios. It has no dependency on the PTL Trader work and should be released first.
- **The existing CSV export is not modified or removed.** It stays useful for spreadsheet analysis. The JSON export is additive.
- **PHPUnit cannot run in this environment.** The vendored PHPUnit is 4.4, which calls `each()`, removed in PHP 8. Verification uses a standalone PHP script run directly with `php`, not `vendor/bin/phpunit`. Do not attempt to upgrade PHPUnit — that is out of scope.
- **`uid` needs no encoding.** OUIDs are 96-bit values stored base64-encoded as 16 ASCII characters in a `binary(16)` column, so `SELECT` returns a ready-to-use string (e.g. `Uuzfqqf1f11l2Jk4`).
- **`last_model_state` must be emitted as a JSON object, not a JSON-encoded string.** It is stored as `text` containing JSON; it must be `json_decode`d before being nested, exactly as `Api\Portfolios::index()` does.
- Do not commit `vendor/` or `node_modules/` changes.

## File Structure

| File | Responsibility |
|---|---|
| `src/Quantverse/Ptl/Controllers/PortManagerController.php` | Modify: add pure `buildExportDocument()` + `cmd_export_json()` action |
| `templates/portManager.tpl` | Modify: add "Export to JSON" button, handler and hidden form |
| `tests/manual/export_json_shape.php` | Create: standalone verification script for the pure builder |

---

### Task 1: Pure export-document builder

Extract the shaping logic into a static method with no database, no globals and no
framework dependencies, so it can be verified by running a plain PHP script.

**Files:**
- Modify: `src/Quantverse/Ptl/Controllers/PortManagerController.php`
- Test: `tests/manual/export_json_shape.php` (create)

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `PortManagerController::buildExportDocument(array $portfolio, array $strategies, array $portfolioFeatures): array` — returns a list containing exactly one portfolio object, shaped like one element of the `GET /portfolios` response. Task 2 calls this.

- [ ] **Step 1: Write the failing verification script**

Create `tests/manual/export_json_shape.php`:

```php
<?php
/**
 * Standalone shape check for PortManagerController::buildExportDocument().
 * Run with:  php tests/manual/export_json_shape.php
 * Exits 0 on success, 1 on first failure. Does not need a database or PHPUnit.
 */

require_once __DIR__ . '/../../src/Quantverse/Ptl/Controllers/PortManagerController.php';

use Quantverse\Ptl\Controllers\PortManagerController;

$failures = 0;
function check($label, $actual, $expected) {
    global $failures;
    if ($actual === $expected) {
        echo "ok   - $label\n";
    } else {
        $failures++;
        echo "FAIL - $label\n";
        echo "       expected: " . var_export($expected, true) . "\n";
        echo "       actual:   " . var_export($actual, true) . "\n";
    }
}

$portfolio = [
    'uid'            => 'Uuzfqqf1f11l2Jk4',
    'user_uid'       => 'SECRETUSERUID000',
    'name'           => 'My Portfolio',
    'max_pairs_open' => 10,
    'account_alloc'  => 100,
    'account_code'   => 'DU123456',
    'master_status'  => 2,
    'pdt_rules'      => 1,
    'created'        => '2020-01-01 00:00:00',
    'last_updated'   => '2026-09-01 12:00:00',
];

$strategies = [
    [
        'uid'              => 'Sssfqqf1f11l2Jk4',
        'portfolio_uid'    => 'Uuzfqqf1f11l2Jk4',
        'model'            => 'Ratio',
        'ticker1'          => 'NYSE:V',
        'ticker2'          => 'NYSE:MA',
        'trade_as_1'       => 0,
        'trade_as_2'       => 0,
        'entry_threshold'  => 2.0,
        'status'           => 2,
        'deleted'          => 0,
        'last_model_state' => '{"@class":".PairTradingModelKalmanAutoState","ve":0.001}',
        'features'         => ['RSI1'],
    ],
];

$doc = PortManagerController::buildExportDocument($portfolio, $strategies, ['PFEATURE']);

check('returns a list of exactly one portfolio', count($doc), 1);
$p = $doc[0];

check('portfolio uid preserved',        $p['uid'],            'Uuzfqqf1f11l2Jk4');
check('portfolio name preserved',       $p['name'],           'My Portfolio');
check('max_pairs_open preserved',       $p['max_pairs_open'], 10);
check('pdt_rules preserved',            $p['pdt_rules'],      1);
check('account_alloc preserved',        $p['account_alloc'],  100);
check('master_status preserved',        $p['master_status'],  2);
check('portfolio features attached',    $p['features'],       ['PFEATURE']);
check('user_uid stripped',              array_key_exists('user_uid', $p),     false);
check('created stripped',               array_key_exists('created', $p),      false);
check('last_updated stripped',          array_key_exists('last_updated', $p), false);

check('one strategy nested', count($p['strategies']), 1);
$s = $p['strategies'][0];

check('strategy uid preserved',      $s['uid'],             'Sssfqqf1f11l2Jk4');
check('model preserved',             $s['model'],           'Ratio');
check('ticker1 preserved',           $s['ticker1'],         'NYSE:V');
check('trade_as_1 preserved',        $s['trade_as_1'],      0);
check('status preserved',            $s['status'],          2);
check('strategy features preserved', $s['features'],        ['RSI1']);
check('deleted stripped',            array_key_exists('deleted', $s),       false);
check('portfolio_uid stripped',      array_key_exists('portfolio_uid', $s), false);

check('last_model_state decoded to array', is_array($s['last_model_state']), true);
check('last_model_state @class kept',      $s['last_model_state']['@class'],
                                           '.PairTradingModelKalmanAutoState');

// A null model state must stay null, not become an empty array.
$strategies2 = $strategies;
$strategies2[0]['last_model_state'] = null;
$doc2 = PortManagerController::buildExportDocument($portfolio, $strategies2, []);
check('null last_model_state stays null', $doc2[0]['strategies'][0]['last_model_state'], null);

// The whole document must survive a JSON encode/decode round trip.
$json = json_encode($doc);
check('document is JSON-encodable', $json !== false, true);
check('re-decoded uid matches', json_decode($json, true)[0]['uid'], 'Uuzfqqf1f11l2Jk4');

echo $failures === 0 ? "\nAll checks passed.\n" : "\n$failures check(s) failed.\n";
exit($failures === 0 ? 0 : 1);
```

- [ ] **Step 2: Run it to verify it fails**

```bash
cd ../pairtradinglab && php tests/manual/export_json_shape.php
```

Expected: a fatal error, `Call to undefined method ...::buildExportDocument()`.

- [ ] **Step 3: Add the pure builder**

In `src/Quantverse/Ptl/Controllers/PortManagerController.php`, add this method to the
class (place it immediately after the existing `cmd_export()` method):

```php
    /**
     * Shapes raw database rows into the JSON export document.
     *
     * The result matches one element of the GET /portfolios API response, which is
     * the shape PTL Trader's PortfolioList.updateFromJson() parses. Pure: no
     * database access, no globals, no framework dependencies, so it is directly
     * testable.
     *
     * @param array $portfolio         row from `portfolios`
     * @param array $strategies        rows from `portfolios_strategies`, each already
     *                                 carrying a 'features' key
     * @param array $portfolioFeatures feature list for the portfolio
     * @return array a list containing exactly one portfolio object
     */
    public static function buildExportDocument(array $portfolio, array $strategies, array $portfolioFeatures) {
        // Columns that are internal to the server and must not leave it.
        $portfolioInternal = ['user_uid', 'created', 'last_updated'];
        $strategyInternal  = ['portfolio_uid', 'deleted'];

        $out = $portfolio;
        foreach ($portfolioInternal as $k) {
            unset($out[$k]);
        }
        $out['features'] = array_values($portfolioFeatures);
        $out['strategies'] = array();

        foreach ($strategies as $s) {
            foreach ($strategyInternal as $k) {
                unset($s[$k]);
            }
            // Stored as a JSON string; the client expects a nested object.
            // A null or empty state must stay null rather than become [].
            if (isset($s['last_model_state']) && $s['last_model_state'] !== '') {
                $s['last_model_state'] = json_decode($s['last_model_state'], true);
            } else {
                $s['last_model_state'] = null;
            }
            if (!isset($s['features'])) {
                $s['features'] = array();
            }
            $out['strategies'][] = $s;
        }

        return array($out);
    }
```

- [ ] **Step 4: Run the verification script to confirm it passes**

```bash
cd ../pairtradinglab && php tests/manual/export_json_shape.php
```

Expected: every line starts `ok   -`, ending with `All checks passed.` and exit code 0.
Confirm with `echo $?` that the exit code is `0`.

- [ ] **Step 5: Commit**

```bash
cd ../pairtradinglab
git add src/Quantverse/Ptl/Controllers/PortManagerController.php tests/manual/export_json_shape.php
git commit -m "Add pure JSON export document builder for portfolios

Shapes portfolio and strategy rows into the same structure the
GET /portfolios API returns, which is what PTL Trader parses. Kept
pure so it can be verified without a database or PHPUnit (the
vendored PHPUnit 4.4 does not run on PHP 8)."
```

---

### Task 2: The `export_json` action and its UI button

**Files:**
- Modify: `src/Quantverse/Ptl/Controllers/PortManagerController.php`
- Modify: `templates/portManager.tpl:672-676` (the `exportPortfolio` handler), `:1150` (the button row), `:1257-1261` (the hidden form)

**Interfaces:**
- Consumes: `PortManagerController::buildExportDocument()` from Task 1.
- Produces: the URL `\/index.php?command=portmanager&pmcmd=export_json&puid=<uid>`, which streams `portfolio.json` as an attachment.

- [ ] **Step 1: Add the controller action**

In `src/Quantverse/Ptl/Controllers/PortManagerController.php`, add this method
immediately after `buildExportDocument()`. It mirrors the structure of the existing
`cmd_export()`, including its deliberate bypass of the Foundation finalize pattern,
and reuses the queries from `Api\Portfolios::index()`:

```php
    /**
     * Exports a single portfolio and its strategies as JSON.
     *
     * The emitted document is identical in shape to one element of the
     * GET /portfolios API response, so it can be imported directly by PTL Trader.
     * Like cmd_export(), this method streams and therefore does not use the
     * Foundation finalize pattern.
     */
    function cmd_export_json() {
        $a = $this->a();

        $userid = $a->getLoggedUserUid();
        $uid = $a->param('puid');

        $db = Kvdbi::getPTLDBInstance();
        $db->query("SET time_zone = 'UTC'");

        // Scope by user_uid as well as uid, so a guessed puid cannot export
        // another user's portfolio.
        $q = $db->qprintf("SELECT * FROM portfolios WHERE uid='%s' AND user_uid='%s' LIMIT 1", $uid, $userid);
        $portfolio = $db->getRow($q);
        if (!$portfolio) {
            header('HTTP/1.1 404 Not Found');
            header('Content-Type: text/plain');
            echo "Portfolio not found";
            return;
        }

        $q = $db->qprintf("SELECT s.* FROM portfolios_strategies AS s
                            INNER JOIN portfolios AS p
                            ON (p.uid=s.portfolio_uid)
                            WHERE s.portfolio_uid='%s' AND s.deleted=0
                            ORDER BY s.ticker1, s.ticker2", $uid);
        $slist = $db->getArray($q);

        $flg = new \Quantverse\Ptl\FeatureListGenerator();
        foreach ($slist as $k => $s) {
            $slist[$k]['features'] = $flg->getStrategyFeatures($s);
        }

        $doc = self::buildExportDocument($portfolio, $slist, $flg->getPortfolioFeatures($portfolio));

        header('Content-Description: File Transfer');
        header('Content-Type: application/json');
        header('Content-Disposition: attachment; filename=portfolio.json');
        header('Pragma: Public');
        header('Expires: 0');

        echo json_encode($doc, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES);
    }
```

- [ ] **Step 2: Verify the method resolves and the class still parses**

```bash
cd ../pairtradinglab
php -l src/Quantverse/Ptl/Controllers/PortManagerController.php
php -r 'require "src/Quantverse/Ptl/Controllers/PortManagerController.php";
        $m = get_class_methods("Quantverse\Ptl\Controllers\PortManagerController");
        var_dump(in_array("cmd_export_json", $m), in_array("buildExportDocument", $m));'
```

Expected: `No syntax errors detected`, then `bool(true)` twice.

- [ ] **Step 3: Re-run the Task 1 verification script**

```bash
cd ../pairtradinglab && php tests/manual/export_json_shape.php
```

Expected: still `All checks passed.` — adding the action must not have changed the builder.

- [ ] **Step 4: Add the UI button and its handler**

In `templates/portManager.tpl`, add a second handler beside `exportPortfolio`
(which currently sits at lines 672-676):

```javascript
    self.exportPortfolioJson = function(p) {
        $('#iExportJsonPUid').val(p.uid);
        $('#jsonExportF').submit();
    };
```

Add the button immediately after the existing "Export to CSV" button (line 1150):

```html
                                <button data-bind="disable: $root.portfolioBusy, click: $root.exportPortfolioJson" class="tiny radius"><i class="fi-page-export onleft2"></i>Export to JSON</button>
```

Add the hidden form immediately after the existing `csvExportF` form (lines 1257-1261):

```html
    <form id="jsonExportF" action="/index.php" target="_blank">
        <input type="hidden" name="command" value="portmanager">
        <input type="hidden" name="pmcmd" value="export_json">
        <input type="hidden" id="iExportJsonPUid" name="puid" value="">
    </form>
```

- [ ] **Step 5: Verify end to end against a running instance**

Bring up the development stack and confirm the download works and the file is
importable-shaped:

```bash
cd ../pairtradinglab
docker compose -f docker-compose.dev.v2.yml up -d
# Log in through the web UI, open the Portfolio Manager, select a portfolio
# that has at least one pair, and click "Export to JSON".
```

Then check the downloaded file:

```bash
python3 -c "
import json,sys
d=json.load(open('/path/to/portfolio.json'))
assert isinstance(d, list) and len(d)==1, 'must be a one-element list'
p=d[0]
for k in ('uid','name','max_pairs_open','master_status','pdt_rules','account_alloc','features','strategies'):
    assert k in p, 'portfolio missing '+k
assert 'user_uid' not in p, 'user_uid must not be exported'
for s in p['strategies']:
    for k in ('uid','model','ticker1','ticker2','trade_as_1','trade_as_2','status',
              'entry_threshold','exit_threshold','downtick_threshold','max_score',
              'ratio_ma_type','ratio_ma_period','ratio_stddev_period','ratio_entry_mode',
              'ratio_rsi_period','ratio_rsi_threshold','residual_linreg_period',
              'neutrality','ka_ve','ka_usage_target','ticker1margin','ticker2margin',
              'allow_reversals','enable_max_days','max_days','enable_min_pl','min_pl',
              'enable_min_price','min_price','enable_min_profit_potential',
              'min_profit_potential','entry_start_hour','entry_start_minute',
              'entry_end_hour','entry_end_minute','exit_start_hour','exit_start_minute',
              'exit_end_hour','exit_end_minute','timezone','allow_positions',
              'slot_occupation','features'):
        assert k in s, 'strategy '+s.get('uid','?')+' missing '+k
    assert s['last_model_state'] is None or isinstance(s['last_model_state'], dict), \
        'last_model_state must be an object or null, not a string'
print('export shape OK:', len(p['strategies']), 'strategies')
"
```

Expected: `export shape OK: N strategies`. Every field listed is one that
`PairStrategy.updateFromJson()` reads with `n.get(...)`, which throws on a missing
node — so a missing field here becomes a crash at import time.

Also confirm exporting a portfolio belonging to a *different* user returns 404
rather than that user's data.

- [ ] **Step 6: Commit**

```bash
cd ../pairtradinglab
git add src/Quantverse/Ptl/Controllers/PortManagerController.php templates/portManager.tpl
git commit -m "Add JSON portfolio export to the portfolio manager

Emits the same document shape as GET /portfolios so the file can be
imported directly by PTL Trader once the service closes. Scoped by
user_uid so a guessed puid cannot export another user's portfolio.
The CSV export is unchanged."
```

---

## Notes for whoever runs this

The CSV export is lossy for migration purposes and stays that way deliberately: it
omits `uid`, `status`, `downtick_threshold`, `allow_reversals`, `trade_as_1`,
`trade_as_2` and every portfolio-level field. Do not try to extend it instead —
the trader consumes the JSON shape.

Once this ships, users need to be told to export before the shutdown date. That
announcement is outside this plan but is the reason it exists.
