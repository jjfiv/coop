
/**
 * Usage: <Button visible={true,false} disabled={true,false} onClick={whatFn} label={text label} />
 */
class Button extends React.Component{
    render() {
        var classes = [];
        classes.push(this.props.visible ? "normal" : "hidden");
        return <input
            className={strjoin(classes)}
            disabled={this.props.disabled}
            type={"button"}
            title={this.props.title || this.props.label}
            onClick={this.props.onClick}
            value={this.props.label}
            />;
    }
}
Button.defaultProps = {
    visible: true,
    disabled: false
};


class IntegerInput extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            text: ""+this.props.start,
            validInt: true
        };
    }
    handleChange(evt) {
        var x = parseInt(evt.target.value);
        if(_.isNaN(x)) {
            this.setState({
                validInt: false,
                text: evt.target.value.trim().toLowerCase()
            });
            return;
        }
        if(x < this.props.min) { x = this.props.min; }
        if(x > this.props.max) { x = this.props.max; }
        this.setState({validInt: true, text: ""+x});
        this.props.onChange(x);
    }
    render() {
        let cls = [];
        if(!this.state.validInt) {
            cls.push("negative");
        }
        var spanCls = "normal";
        if(!this.props.visible) {
            spanCls = "hidden";
        }
        return <span className={spanCls}>
            {this.props.label}
            <input type={"text"}
                   className={strjoin(cls)}
                   style={{width:"4em"}}
                   value={this.state.text}
                   onChange={evt => this.handleChange(evt)} />
        </span>
    }
}
IntegerInput.defaultProps = {
    start: 0,
    onChange: function(val) { },
    min: 0,
    max: 2 << 30
};

class SelectWidget extends React.Component {
    render() {
        let ropts = _.map(this.props.opts, (val, key) => {
            return <option key={key} value={key}>{val}</option>
        });
        return <select onChange={(evt) => this.props.onChange(evt.target.value)}
                       value={this.props.selected}
            >{ropts}</select>;
    }
}
SelectWidget.propTypes = {
    opts: React.PropTypes.objectOf(React.PropTypes.string).isRequired,
    selected: React.PropTypes.string.isRequired,
    onChange: React.PropTypes.func.isRequired
};

class PagedListView extends React.Component {
    render() {
        let rawItems = this.props.items;

        let count = _.size(rawItems);
        let pageSize = this.props.pageSize;
        let totalPages = Math.ceil(count / pageSize) | 0;
        let page = Math.min(Math.max(0, this.props.page), totalPages-1);
        let startIndex = page * pageSize;
        if(startIndex > count) {
            return <span>Page Error</span>;
        }
        let endIndex = Math.min(startIndex + pageSize, count);

        let items = _.map(_.slice(rawItems, startIndex, endIndex), (item, idx) => {
            return <div key={this.props.keyFn(item)}>{
                this.props.renderItem(item, startIndex+idx)
            }</div>;
        });
        _.range(_.size(items), pageSize).forEach((i) => {
            items.push(<div key={"blank"+i}>&nbsp;</div>)
        });

        // TODO max pageButtons
        let pageButtons = _(_.range(0, totalPages)).map(pnum => {
            return <Button
                disabled={pnum === page}
                key={pnum}
                label={pnum+1}
                onClick={evt => this.props.updatePage(pnum)}
                />;
        }).value();

        pageButtons.unshift(
            <Button key={"back"}
                    disabled={totalPages <= 1 || page == 0}
                    label={"<"}
                    onClick={evt => this.props.updatePage(page-1)}
            />);
        pageButtons.push(
            <Button key={"forward"}
                    disabled={totalPages <= 1 || page+1 == totalPages}
                    label={">"}
                    onClick={evt => this.props.updatePage(page+1)}
                />);

        return <div className="PagedListView">
            <div className="items">{items}</div>
            <div className="pages">{pageButtons}</div>
            </div>;
    }
}
PagedListView.propTypes = {
    pageSize: React.PropTypes.number,
    items: React.PropTypes.array.isRequired,
    renderItem: React.PropTypes.func,
    keyFn: React.PropTypes.func,
    updatePage: React.PropTypes.func.isRequired
};
PagedListView.defaultProps = {
    renderItem: ((item, idx) => (idx+1)+". "+item),
    keyFn: (item) => item,
    pageSize: 10
};

class FilterableList extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            filter: '',
            alpha: false,
            page: 0,
            pageSize: 10 || this.props.maxValues
        }
    }
    render() {
        if(!this.props.items) {
            return <span />;
        }
        let filter = this.state.filter;
        let itemFn = this.props.getItemText;

        let total = _.size(this.props.items);
        let matchCount = 0;

        let matchingItems = _(this.props.items)
            .filter(function(item) {
                if(_.isEmpty(filter) || _.contains(itemFn(item).toLowerCase(), filter)) {
                    matchCount++;
                    return true;
                }
                return false;
            })
            .value();

        if(this.state.alpha) {
            matchingItems = _.sortBy(matchingItems, itemFn);
        }

        return <div className={"FilterableList"}>
            <Button label={"A->Z"} onClick={evt => this.setState({alpha: true, page: 0})} />
            <input type="text"
                   onChange={(evt) => this.setState({
                   filter: evt.target.value.toLowerCase(), page: 0
                   })}
                   value={this.state.filter}
                   placeholder={"Filter..."}
                   title={"Filter..."}
                   />

            {(matchCount !== total)?<span>{matchCount} of {total}</span>:<span/>}
            <PagedListView page={this.state.page}
                           pageSize={this.state.pageSize}
                           items={matchingItems}
                           renderItem={this.props.renderItem}
                           updatePage={(index => this.setState({page: index})
            )} />
            </div>;
    }
}
FilterableList.defaultProps = {
    getItemText: _.identity
};

