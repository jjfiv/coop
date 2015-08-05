class LabelMaker {
  constructor() {
    console.log("Hello World!");
    console.log(<h1 />);
  }
}

$(function() {
  let lm = new LabelMaker();
  console.log(lm);
});
