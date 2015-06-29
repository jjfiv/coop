var SearchResults = React.createClass({
    render: function() {
        var items = _.map(this.props.results, function(item, result_idx) {
            return <ResultView key={result_idx} tokens={item} />
            //return <SentenceView key={result_idx} tokens={item} />;
            //return <pre key={result_idx}>{JSON.stringify(item)}</pre>;
        });
        return <div>{items}</div>;
    }
});

/** This starts off with the sentence that is the current hit, but it may request more sentences on either side: */
var ResultView = React.createClass({
    getInitialState: function () {
        EVENTS.register('onPulledSentence', this.onPulledSentence);
        return {
            requestedSentences: [],
            sentences: [this.props.tokens],
            didNotHaveNext: false
        }
    },
    onPulledSentence: function (tokens) {
        var sentenceId = tokens[0].sentenceId;
    },
    loadPrevious: function() {

    },
    loadNext: function() {

    },
    render: function () {
        var items = [];

        items.push(<i>{" #"+this.state.sentences[0][0].sentenceId}</i>);

        items.push(this.state.sentences.map(function(tokens, idx) {
            return <SentenceView key={idx} tokens={tokens} />
        }, this));

        items.push(<div key="buttons" style={{textAlign: "right"}}>
            <Button
            label={"<"}
            title={"Load Previous Sentence"}
            onClick={this.loadPrevious}
            disabled={this.state.sentences[0].sentenceId==0} />

            <Button
            label={"Label"}
            title={"Label this Sentence"}
            onClick={this.labelSentence} />

            <Button
            label={"Save"}
            title={"Save this Sentence"}
            onClick={this.labelSentence} />

            <Button
            label={"Note"}
            title={"Add a Note to this Sentence"}
            onClick={this.labelSentence} />

            <Button
            label={">"}
            title={"Load Next Sentence"}
            onClick={this.loadNext}
            disabled={this.state.didNotHaveNext} />
        </div>);


        return <div className={"resultView"}>{items}</div>
    }
});

var SentenceView = React.createClass({
    render: function() {
        var terms = _.map(this.props.tokens, function(term, term_idx) {
            return <LabelingToken
                key={term_idx} token={term} />
        });
        return <div className="sentenceView">{terms}</div>;
    }
});