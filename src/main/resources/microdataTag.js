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
            return <div className={"error"}>Error</div>;
        }
        var mtag = parseMicrodataTag(tag);
        if(mtag) {
            return <MicrodataTagView data={mtag} />;
        }
        return <div>{tag}</div>;
    }
});

var MicrodataTagView = React.createClass({
    render() {
        var data = this.props.data;
        var cat = <a href={"http://schema.org/"+data.category}>{data.category}</a>;
        var attr = <a href={"http://schema.org/"+data.attribute}>{data.attribute}</a>;
        if(data.attribute) {
            return <div>{[cat, <span>{" / "}</span>, attr]}</div>;
        } else {
            return <div>{cat}</div>;
        }
    }
});