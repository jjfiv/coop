function isMicrodataTag(tagName) {
    return _.startsWith(tagName, "<m>:");
}

function stripPrefix(str, prefix) {
    if(_.startsWith(str, prefix)) {
        return str.substring(prefix.length);
    }
    return str;
}

function parseMicrodataTag(tagStr) {
    if(!isMicrodataTag(tagStr)) return null;

    var obj = {};
    var pretty = stripPrefix(tagStr, "<m>:");
    var parts = pretty.split(":");

    obj.category = stripPrefix(parts[0], "schema.org/");
    obj.attribute = parts[1];
    return obj;
}

var TagView = React.createClass({
    render() {
        var tag = this.props.tag;
        if(!tag) {
           return <span className={"error"}>Error</span>;
        }
        var mtag = parseMicrodataTag(tag);
        if(mtag) {
            return <MicrodataTagView data={mtag} />;
        }
        return <span>{tag}</span>;
    }
});

var MicrodataTagView = React.createClass({
    render() {
        var data = this.props.data;
        var cat = <a key={"cat"} href={"http://schema.org/"+data.category}>{data.category}</a>;
        var attr = <a key={"attr"} href={"http://schema.org/"+data.attribute}>{data.attribute}</a>;
        if(data.attribute) {
            return <span>{[cat, <span key={"slash"}>{" / "}</span>, attr]}</span>;
        } else {
            return <span>{cat}</span>;
        }
    }
});

var TagsAvailable = React.createClass({
    getInitialState() {
        return {
            tags: null,
            filter: '',
        }
    },
    componentDidMount() {
        EVENTS.register('listTagsResponse', this.onListTags);
        EVENTS.signal('listTagsRequest');
    },
    componentWillUnmount() {
        EVENTS.unregister('listTagsResponse', this.onListTags);
    },
    onListTags(tags) {
        this.setState({tags: tags});
    },
    handleChange(evt) {
        this.setState({filter: evt.target.value.toLowerCase()});
    },
    render() {
        if(this.state.tags == null) {
            return <span>Loading...</span>;
        } else {
            var items = [];

            items.push(<HelpButton text={"Tags are like labels, but they are generally pre-defined for a given collection. You can use them to help build your own labels."}/>);

            var lf = this.state.filter;

            var matchCount = 0;
            var total = _.size(this.state.tags);

            var tags = _(this.state.tags).map(function(tag) {
                var matching = _.isEmpty(lf) || _.contains(tag.toLowerCase(),lf);
                var style = matching ? "normal" : "none";
                if(matching) {
                    matchCount += 1;
                }
                return <div key={tag} className={style}><TagView
                    tag={tag} /></div>;
            }, this).value();

            var filterItems = [];
            filterItems.push(<input key={"input"} type="text" onChange={this.handleChange} value={this.state.filter} placeholder={"Filter Tags"} title={"Filter Tags"} />);
            if(matchCount != total) {
                filterItems.push(<span className={"info"}>{" Displaying "+matchCount+" out of "+total+" tags."}</span>)
            }
            items.push(<div>{filterItems}</div>);
            items.push(<div className={"content"}>{tags}</div>);


            return <div className={"content"}>{items}</div>;
        }
    }
});

