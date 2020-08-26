package org.apache.rocketmq.connect.es.model;

import org.apache.rocketmq.connect.es.SinkProcessor;
import org.apache.rocketmq.connect.es.SyncMetadata;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

public abstract class AbstractModel implements Model {

	GetRequest getGetRequest(SyncMetadata syncMetadata) {
		return new GetRequest().index(syncMetadata.getIndex()).id(syncMetadata.getId());
	}

	IndexRequest getIndexRequest(SyncMetadata syncMetadata) {
		return new IndexRequest().index(syncMetadata.getIndex()).id(syncMetadata.getIndex())
				.source(syncMetadata.getRowData().toJSONString(), XContentType.JSON);
	}

	UpdateRequest getUpdateRequest(SyncMetadata syncMetadata) {
		IndexRequest indexRequest = new IndexRequest();
		indexRequest.index(syncMetadata.getIndex()).id(syncMetadata.getIndex())
				.source(syncMetadata.getRowData().toJSONString(), XContentType.JSON);
		return new UpdateRequest().index(syncMetadata.getIndex()).id(syncMetadata.getIndex()).upsert(indexRequest);
	}

	DeleteRequest getDeleteRequest(SyncMetadata syncMetadata) {
		return new DeleteRequest().index(syncMetadata.getIndex()).id(syncMetadata.getIndex());
	}

	SearchRequest getSearchRequest(String indexs, String name, String value) {
		TermQueryBuilder termQueryBuilder = QueryBuilders.termQuery(name, value);
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
		searchSourceBuilder.query(termQueryBuilder);
		searchSourceBuilder.fetchSource(false);
		searchSourceBuilder.size(100);
		searchSourceBuilder.sort("_doc");
		return new SearchRequest(new String[] { indexs }, searchSourceBuilder);
	}

	BulkRequest getBulkRequest(SearchHit[] searchHits , SyncMetadata syncMetadata) {
		BulkRequest bulkRequest = new BulkRequest();
		for (SearchHit searchHit : searchHits) {
			bulkRequest.add(new UpdateRequest().index(searchHit.getId()).id(searchHit.getId()));
		}
		return bulkRequest;
	}

	SearchScrollRequest getSearchScrollRequest(SearchResponse searchResponse , Scroll scroll) {
		SearchScrollRequest scrollRequest = new SearchScrollRequest(searchResponse.getScrollId());
        scrollRequest.scroll(scroll);
        return scrollRequest;
	}
	
	@Override
	public void create(SyncMetadata syncMetadata) {
		syncMetadata.getClient().indexAsync(getIndexRequest(syncMetadata), RequestOptions.DEFAULT, new ModelDefaultActionListener<IndexResponse>(syncMetadata));
	}

	@Override
	public void update(SyncMetadata syncMetadata) {
		syncMetadata.getClient().updateAsync(getUpdateRequest(syncMetadata), RequestOptions.DEFAULT, new ModelDefaultActionListener<UpdateResponse>(syncMetadata));

	}

	@Override
	public void delete(SyncMetadata syncMetadata) {
		syncMetadata.getClient().deleteAsync(getDeleteRequest(syncMetadata), RequestOptions.DEFAULT, new ModelDefaultActionListener<DeleteResponse>(syncMetadata));
	}

	void get(SyncMetadata syncMetadata, ActionListener<GetResponse> listener) {
		syncMetadata.getClient().getAsync(getGetRequest(syncMetadata),RequestOptions.DEFAULT,listener);
	}
	
	/**
	 * 发送周期管理
	 * @author laohu
	 *
	 * @param <Response>
	 */
	class ModelDefaultActionListener<Response> implements ActionListener<Response>{

		
		SyncMetadata syncMetadata;
		
		public ModelDefaultActionListener(SyncMetadata syncMetadata) {
			this.syncMetadata = syncMetadata;
		}
		
		@Override
		public void onResponse(Response response) {
			for(SinkProcessor<Object> resultProcessing : syncMetadata.getResultProcessing()) {
				resultProcessing.onResponse(response, syncMetadata);
			}
		}

		@Override
		public void onFailure(Exception e) {
			for(SinkProcessor<Object> resultProcessing : syncMetadata.getResultProcessing()) {
				resultProcessing.onFailure(e, syncMetadata);
			}
		}
	}
	
	
}
