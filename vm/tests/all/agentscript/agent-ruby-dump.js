insight.on('enter', (ctx, frame) => {
  print(`dumping state of Ruby memory when executing ${ctx.name}`);
  heap.record([{
      stack: [
          {
              at: ctx,
              frame: frame
          }
      ]
  }], 2);
}, {
  roots: true,
  statements: true,
  rootNameFilter: `.*welcome`
});
