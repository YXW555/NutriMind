uni.addInterceptor({
  returnValue (res) {
    if (!(!!res && (typeof res === "object" || typeof res === "function") && typeof res.then === "function")) {
      return res;
    }
    return new Promise((resolve, reject) => {
      res.then((payload) => {
        if (!payload) {
          resolve(payload)
          return
        }

        if (!Array.isArray(payload)) {
          resolve(payload)
          return
        }

        payload[0] ? reject(payload[0]) : resolve(payload[1])
      }).catch(reject);
    });
  },
});
