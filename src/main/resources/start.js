
$(function() {
    React.render(<RandomSentences requestCount={5} />, document.getElementById("rsentences"));
    React.render(<SearchSentences />, document.getElementById("sentences"));
});

