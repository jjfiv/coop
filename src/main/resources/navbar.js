var SearchBox = React.createClass({
    getInitialState: function() {
        return { query: "" };
    },
    componentDidMount: function() {
        EVENTS.register('changeQuery', this.changeQuery);
    },
    handleKey: function(evt) {
        // submit:
        if(evt.which == 13) {
            var query = React.findDOMNode(this.refs.search).value.trim();
            if(_.isEmpty(query)) return;

            EVENTS.signal("searchSentences", {query: query});
            EVENTS.signal("changeContent", { p: "search", query: query });
        }
    },
    changeQuery: function(query) {
        this.setState({query: query});
    },
    handleChange: function(evt) {
        this.changeQuery(evt.target.value);
    },
    render: function () {
        return <input
            ref={"search"}
            className={"rightnav"}
            onKeyPress={this.handleKey}
            id={"searchBox"}
            type={"text"}
            title={"Search Sentences"}
            onChange={this.handleChange}
            value={this.state.query}
            placeholder={"Search Sentences"}
            />
    }
});

var NavBar = React.createClass({
    render: function() {
        console.log(this.props.links);
        var linkElems = _(this.props.links).map(function(link) {
            var href = link.url;
            if(_.isArray(link.url)) {
                href = _.first(link.url);
            }
            return <InternalLink key={link.name} styles={["nav"]} page={link.page} label={link.name} args={link.args || {}} />
        }).value();

        linkElems.push(<SearchBox key={"search"} />);

        return <div id={"header"}>{linkElems}</div>;
    }
});

$(function() {
    var links = [
        { name: "Home", "page": "home" },
        { name: "Labels", "page": "labels" }
    ];

    React.render(<NavBar links={links} />, document.getElementById("top"));
    React.render(<API />, document.getElementById("globals"));
});

