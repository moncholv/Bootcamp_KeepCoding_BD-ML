import scrapy
import json

class MmaRankings_BlogSpider(scrapy.Spider):
    name = 'ufc_mma_rankings_blogspider'
    start_urls = ['https://www.tapology.com/rankings/current-top-ten-best-pound-for-pound-mma-and-ufc-fighters']

    def parse(self, response):
        for article in response.css('li.rankingItemsItem'):
            rank_number = article.css('p.rankingItemsItemRank ::text').extract_first()
            fighter_name = article.css('div.rankingItemsItemRow.name h1 a ::text').extract_first().strip().replace(',', '')
            record = article.css('div.rankingItemsItemRow.name h1.right span ::text').extract_first().strip().replace(',', ' /')
            image_url = article.css('div.rankingItemsItemImage img ::attr("src")').extract_first()
            
            print(f"{rank_number},{fighter_name},{record},{image_url}\n", file=filep)

        for next_page in response.css('span.next a'):
            yield response.follow(next_page, self.parse)