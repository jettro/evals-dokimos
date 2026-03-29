# Examples

Below are some examples of messages to send to the bot.

Starting message to get search for a specific whisky.
```json
{
  "message": "I am looking for a beer cask whisky",
  "conversationId": "jettro-123"
}
```

Message to order the found whisky.
```json
{
  "message": "Yes please order 1, my credit card is 1234-5678-9012-3456",
  "conversationId": "jettro-123"
}
```

Message to order the found whisky with a fake credit card number.
```json
{
  "message": "Yes please order 1, my credit card is 1234-5678-9012-3456",
  "conversationId": "jettro-123",
  "ccNumber": "1234-5678-9012-3456"
}
```