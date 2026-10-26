# On-silicon test report: software 1.23 on an NXP J3R452 (JCOP 4.5), 2026-10-27

> Template, filled in during the run. Every cell marked `…` is a result still to write down. The steps and the commands are
> those of `tools/j3r452/` (identify.sh, install.sh, suite.sh); each keeps its log in `tools/j3r452/out/<time>/`.
> The J3R180 column is what software 1.23 measured on card three on 2026-10-22 (`docs/FOXY-CARD-HARDWARE.md`).

## Card

| Item | Value |
|---|---|
| Chip | NXP J3R452, JCOP 4.5 P71 (as sold) |
| Seller, form | … |
| ATR | `…` (set by the seller; recorded, not used to identify the chip) |
| CPLC: fabricator, IC type, OS ID, OS release date, level | `4790` (NXP) / `…` / `…` / `…` / `…` |
| CPLC: IC serial, batch, fabrication date | `…` / `…` / `…` |
| Platform ID (GET DATA `DF20`) | `…` (the certified JCOP 4.5 P71 build is `J3R6000373181200`) |
| GlobalPlatform (card data `66`) | … |
| Secure channel, key version (key information `E0`) | … |
| ISD | `A000000151000000` (…) |
| Key | factory default `4041…4F`: logged in … (yes / no) |
| Installed before the test (`gp --list`) | … |
| Free memory, blank (`80CAFF21`) | … persistent, … volatile (a blank J3R180: 164,176 and 2,486) |

## Environment

| Item | Value |
|---|---|
| Reader | … (contact) |
| Host | macOS …, Java 17 |
| GlobalPlatformPro | v25.10.20 (`~/.foxycard/gp.jar`) |
| Load file | `foxycard-1.23.cap`, 24,390 bytes (70 past a multiple of 256), sha256 `afa3fc7a…570d61` |
| Tools | `tools/j3r452/` at commit `…`; `tools/chip/` (ProofStoreCheck, ChangeCheck) |

## Step a: no other card

| Check | Result |
|---|---|
| `identify.sh`, readers: one card present, in the expected reader | … |
| Card two (serial `09334220`) is in no reader | … |

## Step b: who the chip is, before anything is put on it

| Check | Expected | Result |
|---|---|---|
| CPLC readable with no key | fabricator `4790`, IC type not `D321` (JCOP 4 P71) | … |
| Platform ID | `J3R6…` | … |
| Foxy's applet `F0464F58594341524401` | `6A82` (absent) | … |
| Foxy's opener `F0464F5859434152444D` | `6A82` | … |
| NDEF tag AID `D2760000850101` | `6A82`, or a seller's tag | … |
| Seller's applets (free memory, FIDO2, OTP) | absent on a blank card | … |
| `gp --info` | GP and CPLC as above | … |
| `gp --list` (one login, factory key) | `ISD: A000000151000000` | … |

## Step d: the probes

### Primitives (`J3r452 caps`)

| Primitive | J3R180 | J3R452 |
|---|---|---|
| SHA-256 | given | … |
| RandomData `ALG_SECURE_RANDOM` | given | … |
| KeyAgreement `ALG_EC_SVDP_DH_PLAIN_XY`, [2]G right | given, 83 ms | … |
| KeyAgreement `ALG_EC_PACE_GM`, [2]G + G right | given, 85 to 100 ms | … |
| Cipher `ALG_RSA_NOPAD`, 512-bit key over p·p, cube right | given, 70 to 80 ms | … |
| Signature `ALG_ECDSA_SHA_256`, secp256k1 sign and verify | given | … |
| EC-FP 256 keys with secp256k1 set by hand; key pair made on the chip | given | … |
| Memory to an applet: persistent / clear-on-reset / clear-on-deselect | over 32 KB with the card's applet; 2.5 KB; 3.7 KB | … |
| Transaction commit capacity | not measured (about a dozen status bytes held) | … |
| `JCSystem.getVersion` | | … |

### Fingerprint engine (`tools/probe-bio`, `J3r452 bio`)

| Check | J3R180 (card three, 2026-10-16) | J3R452 |
|---|---|---|
| Load of a package importing `javacardx.biometry` | accepted | … |
| `BioBuilder.buildBioTemplate(FINGERPRINT, 5)` | `6B03` NO_SUCH_BIO_TEMPLATE | … |
| Every type of the 3.0.5 API | `6B03` for all | … |

## Step e: the install

| Step | J3R180 (1.23) | J3R452 |
|---|---|---|
| LOAD and the opener | accepted | … (… s) |
| INSTALL of the card's applet | about 2 s, first try | … (… s; status `…`) |
| SELECT | `01 17` | … |
| The tag `D2760000850101` and its capability container | `000F20003B00340406E104001600FF` | … |
| Free memory after | | … persistent, … volatile |
| Load file sizes tried, and the answer | 24,390: installed | … |

## Step f and g: the chip checks (`suite.sh`)

| Check | J3R180 (1.23) | J3R452 |
|---|---|---|
| `tools/chip/proofstore.sh` | 36 checks, 0 failed | … |
| `tools/chip/changecheck.sh`, of which 1.23's two units and keyset table | 175 checks, 0 failed (64 in the run of 1.23) | … |
| The drawer of 120 pieces paid in one payment | held | … |
| Phone live suite (`--sigall`, optional) | last run on a chip at 1.19, 52 of 52 | … |

## Step h: times (contact reader, the whole round trip)

| Command | J3R180 (1.23 unless said) | J3R452 |
|---|---|---|
| INSTALL of the card's applet (key made) | about 2 s | … |
| First `SET_CARD` (reserves the 12,288-byte store and the book) | about 130 ms (1.20) | … |
| `SET_CARD` with a book | 146 ms | … |
| `RESET_CARD` (zeros the store) | 268 ms | … |
| The guest's held history, ten askings | 4,735 ms (473 an asking) | … |
| The owner's claim, six askings | 2,809 to 2,840 ms | … |
| `SET_LIMIT` with the rate | 84 ms | … |
| `LOAD_PROOF`, three pieces with a new keyset | 221 ms | … |
| `LOAD_PROOF` with the mint's DLEQ proof (P1 = 1), one piece | (bridge log) | … |
| `SPEND_ALL_CHANGE`, one output | 402 to 403 ms (300 to 860 by the hash to the curve) | … |
| `SPEND_ALL_SIGN`, two conversions to cents | 784 ms | … |
| Ten waits and the signature | 6,230 ms | … |
| `LOAD_CHANGE` | 483 to 492 ms | … |
| `GET_BALANCE` P1 = 1, over 122 places | 151 ms | … |
| SIGN (1.9 era), AUTH | about 0.7 s each | … |

## What was learnt

…

## Not exercised

Contactless (NFC) use of this chip; a fingerprint match (there is no engine to match with unless step d says otherwise).
