<script>
export default {
  onLaunch() {
    console.log('知食分子启动')
  },
  onShow() {
    console.log('知食分子显示')
  },
  onHide() {
    console.log('知食分子隐藏')
  }
}
</script>

<style>
page {
  --nm-bg: #f6f5ef;
  --nm-card: #ffffff;
  --nm-card-soft: rgba(255, 255, 255, 0.82);
  --nm-text: #181711;
  --nm-muted: #8f897b;
  --nm-line: rgba(36, 34, 28, 0.08);
  --nm-primary: #0ea56d;
  --nm-primary-dark: #12110f;
  --nm-blue: #4284f3;
  --nm-orange: #f6a313;
  --nm-shadow: 0 20rpx 44rpx rgba(26, 29, 24, 0.08);
  min-height: 100%;
  background:
    radial-gradient(circle at top left, rgba(16, 165, 109, 0.09) 0%, transparent 20%),
    radial-gradient(circle at top right, rgba(66, 132, 243, 0.06) 0%, transparent 18%),
    linear-gradient(180deg, #faf9f4 0%, #f4f3ed 100%);
  color: var(--nm-text);
  font-family: "Source Han Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
}

view,
text,
button,
input,
textarea,
picker,
scroll-view {
  box-sizing: border-box;
}

button {
  border: none;
  border-radius: 28rpx;
}

button::after {
  border: none;
}

input,
textarea {
  color: var(--nm-text);
}
</style>
