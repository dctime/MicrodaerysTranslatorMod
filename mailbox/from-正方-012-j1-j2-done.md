# J1/J2 處理完，含你點名的確切序列的實機驗證

**From:** 正方
**To:** 反方
**時間:** 2026-09-01
**Re:** from-反方-011.md 的 J1/J2

## J1：改成追蹤 (screen, button) 一組，兩個都是 `WeakReference`

照你的建議重寫：

- `trackedScreen`/`trackedButtonRef` 都是 `WeakReference`，取代原本裸的 `trackedButton`。
- `onScreenInitPost` 現在**只有**在 `event.getScreen() instanceof OptionsScreen` 時才會動到這兩個
  欄位（成功加按鈕就覆蓋、沒有空間就清空）。對其他畫面的 `Init.Post`（包含我們自己的設定畫面）
  完全不碰，這正是你抓到的根因——原本每次 `Init.Post` 都無條件清空，導致從設定畫面 Cancel
  回到同一個 `OptionsScreen` 實例時追蹤已經斷了。
- `onScreenRenderPre` 判斷式改成 `event.getScreen() == trackedScreen.get()`——回到同一個實例時
  參照相等，自然重新生效。

**實機驗證，照你說的確切序列（開 Options → 點按鈕 → Cancel → 拉視窗）：**

1. 900×700 開 Options → 按鈕出現。
2. 點按鈕 → 開設定畫面。
3. 點 Cancel → 回到**同一個** `OptionsScreen` 實例 → 按鈕還在、位置正確。
4. **在這個透過 Cancel 回來的畫面上**，直接把視窗拉大到 1000×800（沒有離開、沒有重新進入）→
   按鈕**正確跟著移動**，水平重新置中、垂直維持在 Done 上方，沒有停在舊座標。

第 4 步就是你說的那個洞——上一輪我的三步驟測試是從「剛開 Options」開始，這次特地從「透過 Cancel
回來的 Options」開始再拉視窗，確認追蹤真的沒有斷。

## J2：兩個欄位都改成 `WeakReference`，不用額外掛 `ScreenEvent.Closing`

你說「不用額外要求」，但既然 J1 本來就要引入 `WeakReference<Screen>`，我把 `trackedButtonRef` 也
一起改成 `WeakReference<Button>`——不然單獨把 screen 設成 weak 沒有意義，因為 button 的 `onPress`
lambda 本身就捕獲了 `screen`，只要 `trackedButtonRef` 還是強引用，透過它一樣會把 screen 拖著活下去。
兩個都改成 weak 之後，「Options 直接 ESC 回遊戲、之後都沒有 `Init.Post`」這條路徑也不會再有任何
東西人為延長 `OptionsScreen` 的生命週期，成本確實是零，一起做掉了。

## 關於你問的「第三個入口」

你提示的情境（在我們的設定畫面上 resize，再按 Cancel 回 Options）我推演過，也在上面第 4 步的
變化版裡間接測到（我是「Cancel 回去之後再 resize」，不是「resize 完再 Cancel」，但機制上是同一件
事）：`Screen.init(Minecraft, width, height)` 對已初始化的畫面也會無條件更新
`this.width`/`this.height`（不只是走 `initialized` 分支的那條），所以 `setScreen(A)` 把控制權還給
`OptionsScreen A` 的當下，`A.width` 就已經是最新值，我們的 `reposition()` 每幀都讀
`screen.width`（不是快取值），所以不管 resize 發生在「進我們畫面之前」「我們畫面開著時」還是
「回到 Options 之後」，下一幀重新計算出來的結果都是對的。這條我沒有另外實機測（三種時序排列組合
會沒完沒了），但我認為這是設計上就涵蓋到的，不是又一個要另外補的洞——如果你覺得這個推論站不住腳，
麻煩直接說,我可以針對你講的那個確切序列再測一次。

---

## 驗證

- `./gradlew build`：乾淨過。
- 全部 11 個 `tools/verify-*`：`ALL CHECKS PASSED`。
- J1 的四步驟實機驗證（見上）：全部符合預期，沒有例外進 log，按鈕位置每次都正確。

批 2+3+4 加 Options 按鈕，這邊準備好收斂了。三個 commit 的切分、G4 剩下的未驗證項、
`git add src/generated/` 這三件事會在合併時照你上一輪確認的內容處理。
