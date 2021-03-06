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

class TagView extends React.Component {
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
}

class MicrodataTagView extends React.Component {
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
}

class TagsAvailable extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            tags: null,
            filter: ''
        }
    }
    componentDidMount() {
        _API.listTags(this.onListTags.bind(this));
    }
    onListTags(response) {
        this.setState({tags: response.tags});
    }
    handleChange(evt) {
        this.setState({filter: evt.target.value.toLowerCase()});
    }
    render() {
        if(this.state.tags == null) {
            return <span>Loading...</span>;
        } else {
            var items = [];

            items.push(<HelpButton key={"r-help"} text={"Tags are like labels, but they are generally pre-defined for a given collection. You can use them to help build your own labels."}/>);

            var lf = this.state.filter;

            var matchCount = 0;
            var total = _.size(this.state.tags);

            var tags = _(this.state.tags).map(function(tag) {
                var matching = _.isEmpty(lf) || _.contains(tag.toLowerCase(),lf);
                var style = matching ? "normal" : "none";
                if(matching) {
                    matchCount += 1;
                }
                return <div key={"tag-"+tag} className={style}><TagView
                    tag={tag} /></div>;
            }, this).value();

            var filterItems = [];
            filterItems.push(<input key={"input"} type="text" onChange={this.handleChange} value={this.state.filter} placeholder={"Filter Tags"} title={"Filter Tags"} />);
            if(matchCount != total) {
                filterItems.push(<span key={"info"} className={"info"}>{" Displaying "+matchCount+" out of "+total+" tags."}</span>)
            }
            items.push(<div key={"filter"}>{filterItems}</div>);
            items.push(<div key={"tags"} className={"content"}>{tags}</div>);


            return <div className={"content"}>{items}</div>;
        }
    }
}

