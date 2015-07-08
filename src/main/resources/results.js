var SentenceView = React.createClass({
    render() {
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
    getInitialState () {
        EVENTS.register('onPulledSentence', this.onPulledSentence);
        return {
            requestedSentences: [],
            sentences: [this.props.tokens],
            didNotHaveNext: false
        }
    },
    onPulledSentence (tokens) {
        var sentenceId = tokens[0].sentenceId;
    },
    loadPrevious() {

    },
    loadNext() {

    },
    render () {
        var items = [];

        var docId = this.state.sentences[0][0].documentId;
        var sid = this.state.sentences[0][0].sentenceId;

        if(docId) {
            items.push(<span key="doc-link" page="view" args={{document:sid}} >{"D#"+docId+" "}</span>);
        }
        items.push(<InternalLink key="sentence-link" page="view" args={{id:sid}} label={"S#"+sid} />);

        items.push(this.state.sentences.map(function(tokens, idx) {
            return <SentenceView key={idx} tokens={tokens} />
        }, this));

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
    getDefaultProps() {
        return {
            before: 2,
            after: 2,
            step: 1
        };
    },
    getInitialState() {
        EVENTS.register('pullSentencesResponse', this.onLoadedSentences);
        return {
            loaded: [],
            minId: this.props.id - this.props.before,
            maxId: this.props.id + this.props.after,
            waiting: false,
            sentences: []
        };
    },
    getWanted() {
        return _.range(this.state.minId, this.state.maxId+1);
    },
    getMissing() {
        return _.difference(this.getWanted(), this.state.loaded);
    },
    onLoadedSentences(sentences) {
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

        this.setState({waiting: false});
        var newSentences = [];
        _.forEach(kept, function(s) { newSentences.push(s); });
        _.forEach(this.state.sentences, function(s) { newSentences.push(s); });

        // put them in order:
        var mergedSentences = _.sortBy(newSentences, function(s) { return s[0].sentenceId; });
        this.setState({
            loaded: _.union(this.state.loaded, newLoaded),
            sentences: mergedSentences
        })
    },
    pullMissingSentences() {
        var missing = this.getMissing();
        if(_.isEmpty(missing)) return;
        this.setState({waiting: true});
        EVENTS.signal('pullSentences', missing);
    },
    componentWillMount() {
        this.pullMissingSentences();
    },
    loadBefore() {
        this.setState(
            {minId: _.max([0, this.state.minId - this.props.step])},
            this.pullMissingSentences);
    },
    loadAfter() {
        this.setState({maxId: this.state.maxId + this.props.step},
            this.pullMissingSentences);
    },
    render() {
        var loaded = this.state.loaded;
        var wanted = this.getWanted();

        var items = _(this.state.sentences).map(function(tokens) {
            var sid = tokens[0].sentenceId;
            return <SentenceView special={this.props.id == sid} key={sid} tokens={tokens} />
        }, this).value();

        return <div>
            <Button disabled={this.state.waiting} label={"Load More Before"} onClick={this.loadBefore}/>
            <div>{items}</div>;
            <Button disabled={this.state.waiting} label={"Load More After"} onClick={this.loadAfter}/>
            </div>
    }
});

var SearchResults = React.createClass({
    render() {
        var items = _.map(this.props.results, function(item, result_idx) {
            return <ResultView key={result_idx} tokens={item} />
            //return <SentenceView key={result_idx} tokens={item} />;
            //return <pre key={result_idx}>{JSON.stringify(item)}</pre>;
        });
        return <div>{items}</div>;
    }
});

