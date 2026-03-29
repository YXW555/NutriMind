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
  --nm-bg: #f2f8f4;
  --nm-card: #ffffff;
  --nm-card-soft: #f0f7f2;
  --nm-surface: #e4f0e7;
  --nm-text: #243428;
  --nm-muted: #78887c;
  --nm-line: rgba(82, 117, 92, 0.12);
  --nm-primary: #6ba27b;
  --nm-primary-dark: #43715a;
  --nm-blue: #7ea8c7;
  --nm-orange: #d0a763;
  --nm-danger: #c27d58;
  --nm-shadow: 0 12rpx 30rpx rgba(88, 126, 100, 0.08);
  min-height: 100%;
  background: linear-gradient(180deg, #fbfefc 0%, var(--nm-bg) 100%);
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
  border-radius: 20rpx;
}

button::after {
  border: none;
}

input,
textarea {
  color: var(--nm-text);
}
</style>
