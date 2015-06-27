
$(function() {
    var tabChildren = [
        {name: "Classifiers", content: <ClassifierList />},
        {name: "Sentence Search", content: <SearchSentences />}
    ];

    React.render(<TabComponent children={tabChildren} />, document.getElementById("tabbed"));
});

