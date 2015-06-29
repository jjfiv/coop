var SentenceView = React.createClass({
    render: function() {
        var terms = _.map(this.props.tokens, function(term, term_idx) {
            return <LabelingToken
                key={term_idx} token={term} />
        });
        var styles = ["sentenceView"];
        if(this.props.special) {
            styles.push("special");
        }

        return <div className={strjoin(styles)}>{terms}</div>;
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

        var sid = this.state.sentences[0][0].sentenceId;
        items.push(<a href={"?p=view&id="+sid} key={"num"}>{" #"+sid}</a>);

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

var DocumentView = React.createClass({
    propTypes: {
        id: React.PropTypes.number.isRequired,
        // how many to load before
        before: React.PropTypes.number,
        // how many to load after
        after: React.PropTypes.number
    },
    getDefaultProps: function() {
        return {
            before: 2,
            after: 2
        };
    },
    getInitialState: function() {
        EVENTS.register('pullSentencesResponse', this.onLoadedSentences);
        console.log(this.props);
        return {
            loaded: [],
            minId: this.props.id - this.props.before,
            maxId: this.props.id + this.props.after,
            sentences: []
        };
    },
    getWanted: function() {
        return _.range(this.state.minId, this.state.maxId+1);
    },
    getMissing: function() {
        return _.difference(this.getWanted(), this.state.loaded);
    },
    onLoadedSentences: function(sentences) {
        console.log(sentences);
        var missing = this.getMissing();
        var kept = [];
        var newLoaded = [];
        _.forEach(sentences, function(s) {
            var id = s[0].sentenceId;
            if(_.contains(missing, id)) {
                newLoaded.push(id);
                kept.push(s);
            }
        });
        if(_.size(kept) == 0) return;

        var newSentences = [];
        _.forEach(kept, function(s) { newSentences.push(s); });
        _.forEach(this.state.sentences, function(s) { newSentences.push(s); });

        console.log(newSentences);
        // put them in order:
        var mergedSentences = _.sortBy(newSentences, function(s) { return s[0].sentenceId; });
        this.setState({
            loaded: _.union(this.state.loaded, newLoaded),
            sentences: mergedSentences
        })
    },
    pullMissingSentences: function() {
        console.log("pullSentences");
        console.log(this.getMissing());
        EVENTS.signal('pullSentences', this.getMissing());
    },
    componentWillMount: function() {
        this.pullMissingSentences();
    },
    render: function() {
        var loaded = this.state.loaded;
        var wanted = this.getWanted();

        var items = _(this.state.sentences).map(function(tokens) {
            var sid = tokens[0].sentenceId;
            return <SentenceView special={this.props.id == sid} key={sid} tokens={tokens} />
        }, this)
            .value();
        console.log(items);

        return <div>{items}</div>;
    }
});

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

