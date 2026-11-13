package com.memoagent.conversation;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 「按时间回顾」这一轮的工具包装。
 * <p>
 * {@link AssetQueryTools} 是无状态单例，只管查库、把结果写成给模型看的文本；
 * 但页面还需要知道「这次引用了哪几条记录」，才能显示引用胶囊、点开看清单。
 * 所以每次对话都 new 一个本类，把查询结果同时登记进当轮的 {@link TurnTools}。
 * <p>
 * 只包装 listAssets 与 assetStats：前者会产生引用，后者只是数字，不挂引用。
 */
public class TurnAssetTools {

    private final AssetQueryTools queries;
    private final TurnTools turn;

    public TurnAssetTools(AssetQueryTools queries, TurnTools turn) {
        this.queries = queries;
        this.turn = turn;
    }

    @Tool(
            name = "listAssets",
            description = """
                    按日期范围列出用户这段时间写过的日记、经验和知识，按时间倒序，不走向量检索、\
                    结果覆盖该范围内的全部记录。用户问「这个月我都做了什么」「最近两周干了什么」\
                    「上个月有哪些记录」这类按时间回顾的问题时调用，不要用 searchAssets 代替。\
                    日期格式必须是 yyyy-MM-dd，包含首尾两天。""")
    public String listAssets(
            @ToolParam(description = "起始日期，格式 yyyy-MM-dd，含当天", required = false) String from,
            @ToolParam(description = "结束日期，格式 yyyy-MM-dd，含当天；不填表示今天", required = false) String to,
            @ToolParam(description = "限定类型：diary、experience、knowledge，多个用逗号分隔；不填表示全部", required = false) String kinds,
            @ToolParam(description = "返回条数，默认 30，最多 60", required = false) Integer limit) {
        AssetQueryTools.Listing listing = queries.listing(from, to, kinds, limit);
        // 同一批记录登记成引用，页面才会出现「搜索到 N 条相关引用」
        turn.citeListing(listing.rows());
        // 记录覆盖情况，前端在回复下方用小字显示「命中 X 条，列出 Y 条」
        if (!listing.rows().isEmpty()) {
            turn.markListing(listing.rows().size(), listing.listed());
        }
        return listing.text();
    }

    @Tool(
            name = "searchAssets",
            description = """
                    按语义检索用户自己的日记、经验和知识。用户想找过去的记录、需要相关素材时调用。\
                    query 用一段话描述要找的东西，不要只截关键词。只返回标题和摘要，不要据此编造正文细节。\
                    要按时间回顾（这个月、最近两周）请用 listAssets，不要用这个。""")
    public String searchAssets(
            @ToolParam(description = "要找的内容，一句话描述即可") String query,
            @ToolParam(description = "限定类型：diary、experience、knowledge，多个用逗号分隔；不填表示全部", required = false) String kinds,
            @ToolParam(description = "返回条数，默认 8，最多 20", required = false) Integer limit) {
        return queries.searchAssets(query, kinds, limit);
    }

    @Tool(
            name = "assetStats",
            description = """
                    统计用户一段时间内日记、经验、知识的条数与分布，不返回正文。\
                    用户要复盘、要「这个月做了哪些」「最近的情况」时，先调用它拿到准确数字，\
                    再调用 listAssets 取清单；正文里的条数必须照抄这里的统计，不要自己数。\
                    日期格式必须是 yyyy-MM-dd，包含首尾两天。""")
    public String assetStats(
            @ToolParam(description = "起始日期，格式 yyyy-MM-dd，含当天", required = false) String from,
            @ToolParam(description = "结束日期，格式 yyyy-MM-dd，含当天；不填表示今天", required = false) String to) {
        return queries.assetStats(from, to);
    }
}
