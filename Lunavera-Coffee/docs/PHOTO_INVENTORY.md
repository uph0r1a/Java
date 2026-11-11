# The 33 starter photos, file by file

TODO 6.44. The README (section "Pictures and fonts: where they come from") asks the owner three questions about the starter menu photos:
who made them, on what right they may be used, and whether they may be published with the source code. If they all came from one place,
the answer in the README is enough and this table can be left as it is. **If they came from different places, write the place of each
file in the last column**: the size and the checksum say exactly which picture a line is about, so the answer stays attached to the right
file even after a rename.

What the program can say about the files, and the owner cannot be asked to check by hand:

- 33 files in `src/main/resources/seed-menu/`: 26 `.jfif`, 6 `.jpg`, 1 `.png`, 2.77 MB in all.
- **Three carry content credentials** (C2PA) that name Google's generative AI, with the words for AI-made media and an invisible SynthID
  watermark: `nuoc-ep-cam.jfif`, `nuoc-ep-tao.jfif`, `tra-dao.jfif`. All three also name "Picasa" as the last program that saved them. So
  those three were made or changed with an AI image tool, and the terms of that tool apply to them.
- The other 30 carry no author, camera, licence or source (their metadata was stripped, or never there). That proves nothing either way.
- The logo and the login background (`images/LunaveraLogo.png`, `images/LoginBackground.png`) are plain PNG files with no metadata chunk at all.

`java tools/PhotoInventory.java` prints this table again from the files. `PhotoInventoryTest` fails when this table no longer matches the
files (a photo added, removed or replaced), and checks that the README and this page say the same about the three AI photos.

| File | Bytes | SHA-256 (first 16) | AI content credentials | Where it came from (owner) |
|---|---|---|---|---|
| `bac-siu.jpg` | 41797 | 00e05e24f65173b4 | no | *(to be written)* |
| `berry-cloud-cheesecake-dau.jfif` | 91162 | 5a4f72d95b8720b5 | no | *(to be written)* |
| `berry-sunset-tra-dau-hibiscus.jfif` | 82441 | c3975606cf9e0e98 | no | *(to be written)* |
| `brownie.jfif` | 124827 | ab2195c21b402e02 | no | *(to be written)* |
| `cafe-den.png` | 83745 | 15d845fd181618b3 | no | *(to be written)* |
| `cafe-dua.jpg` | 44456 | 17ce85b7b2780460 | no | *(to be written)* |
| `cafe-kem-pho-mai.jpg` | 32311 | 1320d255ff7d3686 | no | *(to be written)* |
| `cafe-nau.jpg` | 47616 | 3cf763d832c246c3 | no | *(to be written)* |
| `cafe-sua.jpg` | 50705 | 3ec2eda5eed121dc | no | *(to be written)* |
| `choco-croissant.jfif` | 105958 | c45e8a8b449d35f6 | no | *(to be written)* |
| `chocolate-dream-chocolate-mousse.jfif` | 128085 | 85f57bcdaef234dd | no | *(to be written)* |
| `croissant.jfif` | 101540 | 53cbb81f030d7765 | no | *(to be written)* |
| `golden-honey-cake-banh-mat-ong-hanh-nhan.jfif` | 107456 | 3520d690de660352 | no | *(to be written)* |
| `lunavera-fresh-tao-dua-chanh.jfif` | 88300 | 374fd8fda8dc818f | no | *(to be written)* |
| `lunavera-tea-tra-nhai-cam-vang.jfif` | 67396 | bc046f371b9f7e61 | no | *(to be written)* |
| `lunavera-tiramisu-tiramisu-ca-phe.jfif` | 99739 | 55bdebc1f08210c0 | no | *(to be written)* |
| `lychee-garden-tra-vai-hoa-hong.jfif` | 67739 | 9acd81488ddb2290 | no | *(to be written)* |
| `matcha-bliss-matcha-cheesecake.jfif` | 109212 | 9860e34a01a8fe79 | no | *(to be written)* |
| `matcha-croissant.jfif` | 106286 | 45ca15c46ae64343 | no | *(to be written)* |
| `nuoc-ep-cam.jfif` | 91330 | d4a2bf43847645a8 | yes | *(to be written)* |
| `nuoc-ep-chanh-day.jfif` | 69320 | d3962b4ed5dadf26 | no | *(to be written)* |
| `nuoc-ep-dua.jpg` | 38644 | c34c5b52f8b3c5ab | no | *(to be written)* |
| `nuoc-ep-tao.jfif` | 114545 | 490bb3d919e7c90f | yes | *(to be written)* |
| `peach-blossom-tra-dao-cam-sa.jfif` | 61301 | 4d5ad6177ab32093 | no | *(to be written)* |
| `red-velvet-cake.jfif` | 125776 | 27473ff2e65fa67a | no | *(to be written)* |
| `ruby-fresh-dua-hau-dau.jfif` | 104691 | 75980932ba240223 | no | *(to be written)* |
| `sunrise-orange-cam-dua.jfif` | 76850 | 806fbddc6096d280 | no | *(to be written)* |
| `tra-chanh.jfif` | 78125 | 9ba1c63fa2904f48 | no | *(to be written)* |
| `tra-dao.jfif` | 122310 | 83f41362dbbd0ad7 | yes | *(to be written)* |
| `tra-dau.jfif` | 83093 | 4c737471f3f989d4 | no | *(to be written)* |
| `tra-vai.jfif` | 73937 | 8896d4f981ce3931 | no | *(to be written)* |
| `tropical-breeze-tra-dua-chanh-day.jfif` | 68357 | d961dbd288127577 | no | *(to be written)* |
| `tropical-glow-dua-chanh-day.jfif` | 84959 | 7b6eca850533d3b4 | no | *(to be written)* |
